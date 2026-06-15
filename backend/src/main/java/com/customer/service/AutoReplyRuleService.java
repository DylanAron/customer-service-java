package com.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.constant.ApiConst;
import com.customer.entity.AutoReplyRule;
import com.customer.repository.AutoReplyRuleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AutoReplyRuleService {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyRuleService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AutoReplyRuleMapper ruleMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final CopyOnWriteArrayList<RuleSnapshot> rulePool = new CopyOnWriteArrayList<>();

    public AutoReplyRuleService(AutoReplyRuleMapper ruleMapper,
                                RedisTemplate<String, String> redisTemplate) {
        this.ruleMapper = ruleMapper;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void loadRulesToMemory() {
        reloadRules();
    }

    public List<AutoReplyRule> listAll() {
        return ruleMapper.selectList(new LambdaQueryWrapper<AutoReplyRule>()
                .orderByAsc(AutoReplyRule::getPriority)
                .orderByAsc(AutoReplyRule::getId));
    }

    public AutoReplyRule add(AutoReplyRule rule) {
        normalize(rule);
        ruleMapper.insert(rule);
        reloadRules();
        return rule;
    }

    public AutoReplyRule update(Long id, AutoReplyRule incoming) {
        AutoReplyRule rule = ruleMapper.selectById(id);
        if (rule == null) return null;
        if (incoming.getKeywords() != null) rule.setKeywords(incoming.getKeywords());
        if (incoming.getReplyContent() != null) rule.setReplyContent(incoming.getReplyContent());
        if (incoming.getEnabled() != null) rule.setEnabled(incoming.getEnabled());
        if (incoming.getPriority() != null) rule.setPriority(incoming.getPriority());
        if (incoming.getRemark() != null) rule.setRemark(incoming.getRemark());
        normalize(rule);
        ruleMapper.updateById(rule);
        reloadRules();
        return rule;
    }

    public void delete(Long id) {
        ruleMapper.deleteById(id);
        reloadRules();
    }

    public void reloadRules() {
        List<AutoReplyRule> enabledRules = ruleMapper.selectList(new LambdaQueryWrapper<AutoReplyRule>()
                .eq(AutoReplyRule::getEnabled, true)
                .orderByAsc(AutoReplyRule::getPriority)
                .orderByAsc(AutoReplyRule::getId));

        List<RuleSnapshot> snapshots = new ArrayList<>();
        for (AutoReplyRule rule : enabledRules) {
            List<String> keywords = parseKeywords(rule.getKeywords());
            if (keywords.isEmpty() || isBlankHtml(rule.getReplyContent())) {
                continue;
            }
            snapshots.add(new RuleSnapshot(
                    rule.getId(),
                    rule.getPriority() == null ? 100 : rule.getPriority(),
                    keywords,
                    rule.getReplyContent()
            ));
        }
        snapshots.sort(Comparator.comparingInt(RuleSnapshot::priority).thenComparingLong(RuleSnapshot::id));
        rulePool.clear();
        rulePool.addAll(snapshots);
        log.info("Auto reply rule pool loaded: {} enabled rule(s)", rulePool.size());
    }

    public Optional<AutoReplyMatch> match(String userId, String content) {
        if (isBlankText(content) || !claimUserCooldown(userId)) {
            return Optional.empty();
        }
        String normalizedContent = normalizeText(content);
        for (RuleSnapshot rule : rulePool) {
            for (String keyword : rule.keywords()) {
                if (!keyword.isBlank() && normalizedContent.contains(normalizeText(keyword))) {
                    return Optional.of(new AutoReplyMatch(rule.id(), rule.replyContent()));
                }
            }
        }
        releaseUserCooldown(userId);
        return Optional.empty();
    }

    private boolean claimUserCooldown(String userId) {
        String key = ApiConst.REDIS_KEY_AUTO_REPLY_COOLDOWN + userId;
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ApiConst.TTL_AUTO_REPLY_COOLDOWN));
        return Boolean.TRUE.equals(ok);
    }

    private void releaseUserCooldown(String userId) {
        redisTemplate.delete(ApiConst.REDIS_KEY_AUTO_REPLY_COOLDOWN + userId);
    }

    private void normalize(AutoReplyRule rule) {
        rule.setKeywords(toKeywordsJson(parseKeywords(rule.getKeywords())));
        if (rule.getPriority() == null) rule.setPriority(100);
        if (rule.getEnabled() == null) rule.setEnabled(true);
    }

    private List<String> parseKeywords(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            if (raw.trim().startsWith("[")) {
                List<String> parsed = MAPPER.readValue(raw, new TypeReference<List<String>>() {});
                return cleanKeywords(parsed);
            }
        } catch (Exception ignored) {}
        return cleanKeywords(List.of(raw.split("[,，\\n]")));
    }

    private List<String> cleanKeywords(List<String> keywords) {
        return keywords.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private String toKeywordsJson(List<String> keywords) {
        try {
            return MAPPER.writeValueAsString(keywords);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase();
    }

    private boolean isBlankText(String text) {
        return text == null || text.trim().isBlank();
    }

    private boolean isBlankHtml(String html) {
        if (html == null) return true;
        if (html.toLowerCase().contains("<img")) return false;
        String text = html.replaceAll("<[^>]*>", "").replace("&nbsp;", "").trim();
        return text.isBlank();
    }

    private record RuleSnapshot(Long id, Integer priority, List<String> keywords, String replyContent) {}

    public record AutoReplyMatch(Long ruleId, String replyContent) {}
}
