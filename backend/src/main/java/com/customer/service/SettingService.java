package com.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.entity.Setting;
import com.customer.repository.SettingMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 系统设置服务，从 MySQL (cs_setting 表) 读取配置，本地 Caffeine 缓存。
 *
 * <p>设置项：
 * <ul>
 *   <li>welcome_message — 用户进入聊天时的欢迎语</li>
 *   <li>auto_reply_message — 无在线客服时用户的自动回复</li>
 * </ul>
 */
@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);

    private static final String KEY_WELCOME = "welcome_message";
    private static final String KEY_AUTO_REPLY = "auto_reply_message";

    private static final String DEFAULT_WELCOME = "您好，欢迎回来！有什么可以帮您的吗？";
    private static final String DEFAULT_AUTO_REPLY = "当前没有在线客服，请留言，我们会尽快回复您。";

    private final SettingMapper settingMapper;

    /** 本地 Caffeine 缓存，1 小时过期 */
    private final Cache<String, String> localCache;

    public SettingService(SettingMapper settingMapper) {
        this.settingMapper = settingMapper;
        this.localCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
    }

    @PostConstruct
    public void initDefaults() {
        // 先清空本地缓存（防止残留空值）
        localCache.invalidateAll();
        // 如果数据库中没有记录，插入默认值
        initSetting(KEY_WELCOME, DEFAULT_WELCOME);
        initSetting(KEY_AUTO_REPLY, DEFAULT_AUTO_REPLY);
    }

    private void initSetting(String key, String defaultValue) {
        Setting existing = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, key));
        if (existing == null) {
            Setting s = new Setting();
            s.setSettingKey(key);
            s.setSettingValue(defaultValue);
            settingMapper.insert(s);
            log.info("初始化设置: {} = {}", key, defaultValue);
        }
    }

    public String getWelcomeMessage() {
        return getSetting(KEY_WELCOME, DEFAULT_WELCOME);
    }

    public String getAutoReplyMessage() {
        return getSetting(KEY_AUTO_REPLY, DEFAULT_AUTO_REPLY);
    }

    public void setWelcomeMessage(String msg) {
        setSetting(KEY_WELCOME, msg);
    }

    public void setAutoReplyMessage(String msg) {
        setSetting(KEY_AUTO_REPLY, msg);
    }

    public Map<String, String> getAll() {
        return Map.of(
            KEY_WELCOME, getWelcomeMessage(),
            KEY_AUTO_REPLY, getAutoReplyMessage()
        );
    }

    private String getSetting(String key, String defaultVal) {
        // 1. 本地缓存（含空字符串也视为未命中，避免缓存了空值）
        String cached = localCache.getIfPresent(key);
        if (cached != null && !cached.isEmpty()) return cached;

        // 2. 数据库
        Setting setting = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, key));
        if (setting != null && setting.getSettingValue() != null && !setting.getSettingValue().isEmpty()) {
            localCache.put(key, setting.getSettingValue());
            return setting.getSettingValue();
        }

        return defaultVal;
    }

    private void setSetting(String key, String value) {
        // 值为 null 或空字符串时，仍然写入空（表示用户想清空），
        // 但 getSetting 会兜底返回默认值
        // 更新数据库
        Setting existing = settingMapper.selectOne(
                new LambdaQueryWrapper<Setting>().eq(Setting::getSettingKey, key));
        if (existing != null) {
            existing.setSettingValue(value);
            settingMapper.updateById(existing);
        } else {
            Setting s = new Setting();
            s.setSettingKey(key);
            s.setSettingValue(value);
            settingMapper.insert(s);
        }
        // 更新本地缓存
        localCache.put(key, value);
    }

    private String defaultVal(String key) {
        if (KEY_WELCOME.equals(key)) return DEFAULT_WELCOME;
        if (KEY_AUTO_REPLY.equals(key)) return DEFAULT_AUTO_REPLY;
        return "";
    }
}
