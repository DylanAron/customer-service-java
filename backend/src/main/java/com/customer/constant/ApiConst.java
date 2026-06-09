package com.customer.constant;

/**
 * System-wide constants: Redis keys, TTLs, WebSocket paths.
 */
public class ApiConst {

    // ========== WebSocket path prefixes ==========
    public static final String WS_USER_PREFIX = "/ws/user/";
    public static final String WS_AGENT_PREFIX = "/ws/agent/";

    // ========== Redis key prefixes ==========
    /** userId -> agentId, TTL 1 day */
    public static final String REDIS_KEY_ASSIGNMENT_USER = "assignment:user:";
    /** agentId -> Set<userId>, TTL 1 day */
    public static final String REDIS_KEY_ASSIGNMENT_AGENT = "assignment:agent:";
    /** userId -> "1" for online, TTL 2 min */
    public static final String REDIS_KEY_USER_ONLINE = "user:online:";
    /** agentId -> "1" for online, TTL 2 min */
    public static final String REDIS_KEY_AGENT_ONLINE = "agent:online:";
    /** userId -> lastVisitTimestamp, TTL 5 min */
    public static final String REDIS_KEY_USER_LAST_VISIT = "user:last_visit:";
    /** welcome message content */
    public static final String REDIS_KEY_SETTING_WELCOME = "settings:welcome_message";
    /** auto-reply message content */
    public static final String REDIS_KEY_SETTING_AUTO_REPLY = "settings:auto_reply_message";

    // ========== Redis Pub/Sub channels ==========
    public static final String PUBSUB_USER_MESSAGE = "channel:user_msg";
    public static final String PUBSUB_AGENT_MESSAGE = "channel:agent_msg";
    public static final String PUBSUB_NOTIFY = "channel:notify";

    // ========== TTL (seconds) ==========
    /** Online heartbeat TTL — 2 minutes */
    public static final long TTL_ONLINE = 120;
    /** Agent-user assignment TTL — 1 day */
    public static final long TTL_ASSIGNMENT = 86400;
    /** Welcome-message dedup TTL — 5 minutes */
    public static final long TTL_LAST_VISIT = 300;
    /** Local settings cache TTL — 1 hour (in seconds) */
    public static final long TTL_SETTINGS_CACHE = 3600;

    private ApiConst() {}
}
