package com.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.constant.ApiConst;
import com.customer.entity.Agent;
import com.customer.repository.AgentMapper;
import com.customer.repository.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Redis 版客服分配服务，支持多实例共享分配关系。
 */
@Service
public class RedisAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(RedisAssignmentService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final AgentMapper agentMapper;
    private final MessageMapper messageMapper;

    public RedisAssignmentService(RedisTemplate<String, String> redisTemplate,
                                  AgentMapper agentMapper,
                                  MessageMapper messageMapper) {
        this.redisTemplate = redisTemplate;
        this.agentMapper = agentMapper;
        this.messageMapper = messageMapper;
    }

    public RedisTemplate<String, String> getRedisTemplate() {
        return redisTemplate;
    }

    /**
     * 为用户分配在线客服：优先复用在线的既有分配，否则随机选择一个在线且启用的客服。
     */
    public synchronized Long assignAgent(String userId) {
        String existingKey = ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId;
        String agentIdStr = redisTemplate.opsForValue().get(existingKey);
        if (agentIdStr != null) {
            Long existingAgentId = Long.parseLong(agentIdStr);
            if (isAgentOnline(existingAgentId)) {
                return existingAgentId;
            }
            removeUser(userId);
        }

        List<Agent> allEnabledAgents = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>().eq(Agent::isEnabled, true));
        List<Agent> available = allEnabledAgents.stream()
                .filter(a -> isAgentOnline(a.getId()))
                .toList();

        if (available.isEmpty()) {
            log.info("No online agent available for user {}, message queued for later assignment", userId);
            return null;
        }

        Agent selected = available.get(new Random().nextInt(available.size()));
        Long agentId = selected.getId();

        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId,
                String.valueOf(agentId),
                Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));
        redisTemplate.opsForSet().add(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId, userId);
        redisTemplate.expire(
                ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId,
                Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));

        log.info("Assigned user {} to agent {}", userId, agentId);
        return agentId;
    }

    public Long getAssignedAgent(String userId) {
        String val = redisTemplate.opsForValue().get(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
        return val != null ? Long.parseLong(val) : null;
    }

    public List<String> getUsersForAgent(Long agentId) {
        Set<String> users = redisTemplate.opsForSet().members(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId);
        return users != null ? new ArrayList<>(users) : List.of();
    }

    /**
     * 将用户直接分配给指定客服（用于接管离线客服的用户）。
     */
    public void assignAgentToUser(String userId, Long agentId) {
        // 清理旧分配，避免用户同时出现在两个客服的 Set 里
        String oldAgentId = redisTemplate.opsForValue()
                .get(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
        if (oldAgentId != null && !oldAgentId.equals(String.valueOf(agentId))) {
            redisTemplate.opsForSet()
                    .remove(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + oldAgentId, userId);
        }
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId,
                String.valueOf(agentId),
                Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));
        redisTemplate.opsForSet().add(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId, userId);
        redisTemplate.expire(
                ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId,
                Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));
        log.info("Agent {} took over user {}", agentId, userId);
    }

    public void removeUser(String userId) {
        String agentIdStr = redisTemplate.opsForValue().get(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
        if (agentIdStr != null) {
            redisTemplate.opsForSet().remove(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentIdStr, userId);
        }
        redisTemplate.delete(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
    }

    public boolean isAgentOnline(Long agentId) {
        Boolean exists = redisTemplate.hasKey(ApiConst.REDIS_KEY_AGENT_ONLINE + agentId);
        return Boolean.TRUE.equals(exists);
    }

    public boolean isUserOnline(String userId) {
        Boolean exists = redisTemplate.hasKey(ApiConst.REDIS_KEY_USER_ONLINE + userId);
        return Boolean.TRUE.equals(exists);
    }

    public void markAgentOnline(Long agentId) {
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_AGENT_ONLINE + agentId,
                "1",
                Duration.ofSeconds(ApiConst.TTL_AGENT_ONLINE));
    }

    public void markUserOnline(String userId) {
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_USER_ONLINE + userId,
                "1",
                Duration.ofSeconds(ApiConst.TTL_ONLINE));
    }

    public void markAgentOffline(Long agentId) {
        redisTemplate.delete(ApiConst.REDIS_KEY_AGENT_ONLINE + agentId);
    }

    public void markUserOffline(String userId) {
        redisTemplate.delete(ApiConst.REDIS_KEY_USER_ONLINE + userId);
    }

    public void refreshUserOnline(String userId) {
        redisTemplate.expire(
                ApiConst.REDIS_KEY_USER_ONLINE + userId,
                Duration.ofSeconds(ApiConst.TTL_ONLINE));
    }

    public void refreshAgentOnline(Long agentId) {
        redisTemplate.expire(
                ApiConst.REDIS_KEY_AGENT_ONLINE + agentId,
                Duration.ofSeconds(ApiConst.TTL_AGENT_ONLINE));
    }

    public void touchUserLastVisit(String userId) {
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_USER_LAST_VISIT + userId,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(ApiConst.TTL_LAST_VISIT));
    }

    public boolean hasRecentVisit(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ApiConst.REDIS_KEY_USER_LAST_VISIT + userId));
    }

    /**
     * 查询 Redis 心跳仍有效的启用客服。
     */
    public List<Long> findOnlineEnabledAgentIds() {
        List<Agent> allEnabled = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>().eq(Agent::isEnabled, true));
        return allEnabled.stream()
                .map(Agent::getId)
                .filter(this::isAgentOnline)
                .toList();
    }

    /**
     * 给指定在线客服认领一批离线留言用户。
     * batchSize 表示最多认领的用户数，不是消息条数；每个用户被认领后，会把该用户所有未归属消息一起归到该客服。
     *
     * 无需 synchronized 或分布式锁：每个用户的 Redis SET IF ABSENT 是原子操作，
     * 多实例间不会重复分配同一用户。
     */
    public int assignPendingUsers(Long agentId, int batchSize) {
        if (!isAgentOnline(agentId)) {
            log.warn("Agent {} is not online, skipping pending assignment", agentId);
            return 0;
        }

        List<String> unassigned = messageMapper.findUnassignedUserIds(Math.max(batchSize * 10, batchSize));
        if (unassigned.isEmpty()) {
            return 0;
        }

        int assigned = 0;
        for (String userId : unassigned) {
            if (assigned >= batchSize) break;

            Long existingAgentId = getAssignedAgent(userId);
            if (existingAgentId != null) {
                if (isAgentOnline(existingAgentId)) {
                    // Redis 中已有在线客服分配时，补齐数据库归属，避免反复占用候选名额。
                    messageMapper.assignUnassignedMessagesToAgent(userId, existingAgentId);
                    continue;
                }
                removeUser(userId);
            }

            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId,
                    String.valueOf(agentId),
                    Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));
            if (!Boolean.TRUE.equals(locked)) {
                Long latestAgentId = getAssignedAgent(userId);
                if (latestAgentId != null) {
                    messageMapper.assignUnassignedMessagesToAgent(userId, latestAgentId);
                }
                continue;
            }

            redisTemplate.opsForSet().add(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId, userId);
            redisTemplate.expire(
                    ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId,
                    Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));
            // 认领离线留言时，同步补齐历史消息的客服归属，保证列表和会话历史能查到。
            messageMapper.assignUnassignedMessagesToAgent(userId, agentId);
            assigned++;
            log.info("Pending assignment: user {} -> agent {}", userId, agentId);
        }

        if (assigned > 0) {
            log.info("Agent {} claimed {} pending user(s)", agentId, assigned);
        }
        return assigned;
    }
}
