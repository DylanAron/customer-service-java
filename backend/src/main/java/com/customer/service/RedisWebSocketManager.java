package com.customer.service;

import com.customer.constant.ApiConst;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages WebSocket channel mappings and online status for this instance.
 * <p>
 * Each instance only knows its own local channels.
 * Online status is published to Redis for cross-instance awareness.
 * Cross-instance message delivery uses Redis Pub/Sub.
 * </p>
 */
@Service
public class RedisWebSocketManager {

    private static final Logger log = LoggerFactory.getLogger(RedisWebSocketManager.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisAssignmentService assignmentService;

    /** userId -> Netty Channel (this instance only) */
    private final Map<String, Channel> localUserChannels = new ConcurrentHashMap<>();
    /** agentId -> Netty Channel (this instance only) */
    private final Map<Long, Channel> localAgentChannels = new ConcurrentHashMap<>();
    /** channelId -> userId reverse lookup */
    private final Map<String, String> channelUsers = new ConcurrentHashMap<>();
    /** channelId -> agentId reverse lookup */
    private final Map<String, Long> channelAgents = new ConcurrentHashMap<>();

    public RedisWebSocketManager(RedisTemplate<String, String> redisTemplate,
                                  RedisAssignmentService assignmentService) {
        this.redisTemplate = redisTemplate;
        this.assignmentService = assignmentService;
    }

    // ========== Registration ==========

    public void registerUser(String userId, Channel channel) {
        String channelId = channel.id().asShortText();
        localUserChannels.put(userId, channel);
        channelUsers.put(channelId, userId);
        assignmentService.markUserOnline(userId);
        log.debug("User {} registered on this instance", userId);
    }

    public void registerAgent(Long agentId, Channel channel) {
        String channelId = channel.id().asShortText();
        localAgentChannels.put(agentId, channel);
        channelAgents.put(channelId, agentId);
        assignmentService.markAgentOnline(agentId);
        log.debug("Agent {} registered on this instance", agentId);
    }

    // ========== Un-registration ==========

    public String unregisterByChannel(String channelId) {
        if (channelUsers.containsKey(channelId)) {
            String userId = channelUsers.remove(channelId);
            localUserChannels.remove(userId);
            assignmentService.markUserOffline(userId);
            log.debug("User {} unregistered", userId);
            return userId;
        }
        if (channelAgents.containsKey(channelId)) {
            Long agentId = channelAgents.remove(channelId);
            localAgentChannels.remove(agentId);
            assignmentService.markAgentOffline(agentId);
            log.debug("Agent {} unregistered", agentId);
            return String.valueOf(agentId);
        }
        return null;
    }

    // ========== Channel lookups ==========

    public Channel getUserChannel(String userId) {
        return localUserChannels.get(userId);
    }

    public Channel getAgentChannel(Long agentId) {
        return localAgentChannels.get(agentId);
    }

    public boolean hasUserLocally(String userId) {
        return localUserChannels.containsKey(userId);
    }

    public boolean hasAgentLocally(Long agentId) {
        return localAgentChannels.containsKey(agentId);
    }

    public String getUserIdByChannel(String channelId) {
        return channelUsers.get(channelId);
    }

    public Long getAgentIdByChannel(String channelId) {
        return channelAgents.get(channelId);
    }

    // ========== Cross-instance message publishing ==========

    /**
     * Publish a user message to the agent's instance(s) via Redis Pub/Sub.
     * Also tries local delivery first.
     */
    public void publishUserMessage(Long agentId, String messageJson) {
        // Try local delivery
        Channel localCh = localAgentChannels.get(agentId);
        if (localCh != null && localCh.isActive()) {
            localCh.writeAndFlush(new TextWebSocketFrame(messageJson));
            return;
        }
        // Publish to Redis for other instances
        try {
            redisTemplate.convertAndSend(ApiConst.PUBSUB_USER_MESSAGE, messageJson);
        } catch (Exception e) {
            log.error("Failed to publish user message to Redis", e);
        }
    }

    /**
     * Publish an agent message to the user's instance(s) via Redis Pub/Sub.
     * Also tries local delivery first.
     */
    public void publishAgentMessage(String userId, String messageJson) {
        // Try local delivery
        Channel localCh = localUserChannels.get(userId);
        if (localCh != null && localCh.isActive()) {
            localCh.writeAndFlush(new TextWebSocketFrame(messageJson));
            return;
        }
        // Publish to Redis for other instances
        try {
            redisTemplate.convertAndSend(ApiConst.PUBSUB_AGENT_MESSAGE, messageJson);
        } catch (Exception e) {
            log.error("Failed to publish agent message to Redis", e);
        }
    }

    /**
     * Publish a notification (assignment, status change) via Redis Pub/Sub.
     */
    public void publishNotification(String messageJson) {
        try {
            redisTemplate.convertAndSend(ApiConst.PUBSUB_NOTIFY, messageJson);
        } catch (Exception e) {
            log.error("Failed to publish notification to Redis", e);
        }
    }

    /**
     * Deliver a message to a local user channel (called from Pub/Sub listener).
     */
    public void deliverToLocalUser(String userId, String messageJson) {
        Channel ch = localUserChannels.get(userId);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(new TextWebSocketFrame(messageJson));
        }
    }

    /**
     * Deliver a message to a local agent channel (called from Pub/Sub listener).
     */
    public void deliverToLocalAgent(Long agentId, String messageJson) {
        Channel ch = localAgentChannels.get(agentId);
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(new TextWebSocketFrame(messageJson));
        }
    }
}
