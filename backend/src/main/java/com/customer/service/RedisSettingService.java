package com.customer.service;

import com.customer.constant.ApiConst;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed settings service with local Caffeine cache.
 * <p>
 * Settings are stored in Redis for persistence across restarts and instances.
 * A local Caffeine cache reduces Redis reads for frequently accessed settings.
 * Writes go to both Redis and local cache immediately.
 * </p>
 */
@Service
public class RedisSettingService {

    private static final Logger log = LoggerFactory.getLogger(RedisSettingService.class);

    private static final String DEFAULT_WELCOME = "您好，欢迎回来！有什么可以帮您的吗？";
    private static final String DEFAULT_AUTO_REPLY = "当前没有在线客服，请留言，我们会尽快回复您。";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Local Caffeine cache for settings (TTL = 1 hour).
     * Survives as long as the JVM is alive, but caches in Redis are permanent.
     */
    private final Cache<String, String> localCache;

    public RedisSettingService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.localCache = Caffeine.newBuilder()
                .expireAfterWrite(ApiConst.TTL_SETTINGS_CACHE, TimeUnit.SECONDS)
                .maximumSize(100)
                .build();
        initDefaults();
    }

    /** Initialize Redis with defaults if not already set. */
    private void initDefaults() {
        try {
            redisTemplate.opsForValue()
                    .setIfAbsent(ApiConst.REDIS_KEY_SETTING_WELCOME, DEFAULT_WELCOME);
            redisTemplate.opsForValue()
                    .setIfAbsent(ApiConst.REDIS_KEY_SETTING_AUTO_REPLY, DEFAULT_AUTO_REPLY);
        } catch (Exception e) {
            log.warn("Redis not available for settings init, using defaults: {}", e.getMessage());
        }
    }

    public String getWelcomeMessage() {
        return getSetting(ApiConst.REDIS_KEY_SETTING_WELCOME, DEFAULT_WELCOME);
    }

    public String getAutoReplyMessage() {
        return getSetting(ApiConst.REDIS_KEY_SETTING_AUTO_REPLY, DEFAULT_AUTO_REPLY);
    }

    public void setWelcomeMessage(String msg) {
        setSetting(ApiConst.REDIS_KEY_SETTING_WELCOME, msg);
    }

    public void setAutoReplyMessage(String msg) {
        setSetting(ApiConst.REDIS_KEY_SETTING_AUTO_REPLY, msg);
    }

    public Map<String, String> getAll() {
        return Map.of(
                "welcome_message", getWelcomeMessage(),
                "auto_reply_message", getAutoReplyMessage()
        );
    }

    private String getSetting(String redisKey, String defaultVal) {
        // 1. Try local cache
        String cached = localCache.getIfPresent(redisKey);
        if (cached != null) return cached;

        // 2. Try Redis
        try {
            String fromRedis = redisTemplate.opsForValue().get(redisKey);
            if (fromRedis != null) {
                localCache.put(redisKey, fromRedis);
                return fromRedis;
            }
        } catch (Exception ignored) {}

        // 3. Fallback to default
        return defaultVal;
    }

    private void setSetting(String redisKey, String value) {
        // Write to Redis
        try {
            redisTemplate.opsForValue().set(redisKey, value, Duration.ofDays(365));
        } catch (Exception e) {
            log.warn("Failed to write setting {} to Redis: {}", redisKey, e.getMessage());
        }
        // Write to local cache
        localCache.put(redisKey, value);
    }
}
