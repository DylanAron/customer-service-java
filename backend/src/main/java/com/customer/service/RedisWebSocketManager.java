package com.customer.service;

import com.customer.constant.ApiConst;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket channel mappings and online status for this instance.
 * <p>
 * Each instance only knows its own local channels.
 * Online status is published to Redis for cross-instance awareness.
 * Cross-instance message delivery uses Redis Pub/Sub.
 * </p>
 *
 * <p>Supports multi-tab: each user/agent can have multiple WebSocket channels
 * (e.g. multiple browser tabs). Closing one tab does not mark the user/agent
 * offline as long as other tabs remain open.</p>
 */
@Service
public class RedisWebSocketManager {

    private static final Logger log = LoggerFactory.getLogger(RedisWebSocketManager.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisAssignmentService assignmentService;

    /** userId -> Set of Netty Channels (supports multi-tab) */
    private final Map<String, Set<Channel>> localUserChannels = new ConcurrentHashMap<>();
    /** agentId -> Set of Netty Channels (supports multi-tab) */
    private final Map<Long, Set<Channel>> localAgentChannels = new ConcurrentHashMap<>();
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
        localUserChannels.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(channel);
        channelUsers.put(channelId, userId);
        assignmentService.markUserOnline(userId);
        log.debug("User {} registered on this instance (channel {})", userId, channelId);
    }

    public void registerAgent(Long agentId, Channel channel) {
        String channelId = channel.id().asShortText();
        localAgentChannels.computeIfAbsent(agentId, k -> new CopyOnWriteArraySet<>()).add(channel);
        channelAgents.put(channelId, agentId);
        assignmentService.markAgentOnline(agentId);
        log.debug("Agent {} registered on this instance (channel {})", agentId, channelId);
    }

    // ========== Un-registration ==========

    /**
     * Remove a channel by its ID. Returns the userId or agentId (as String) that was unregistered,
     * or null if the channel was not found.
     * <p>
     * Multi-tab: only removes the specific channel from the set.
     * If the agent/user still has other channels open, Redis online status is preserved.
     * Only when the LAST channel closes is the user/agent marked offline.
     */
    public String unregisterByChannel(String channelId) {
        if (channelUsers.containsKey(channelId)) {
            String userId = channelUsers.remove(channelId);
            Set<Channel> channels = localUserChannels.get(userId);
            if (channels != null) {
                channels.removeIf(ch -> ch.id().asShortText().equals(channelId));
                if (channels.isEmpty()) {
                    localUserChannels.remove(userId);
                    assignmentService.markUserOffline(userId);
                    log.debug("User {} fully unregistered (no channels left)", userId);
                } else {
                    log.debug("User {} channel {} closed, {} tab(s) still open, staying online",
                            userId, channelId, channels.size());
                }
            }
            return userId;
        }
        if (channelAgents.containsKey(channelId)) {
            Long agentId = channelAgents.remove(channelId);
            Set<Channel> channels = localAgentChannels.get(agentId);
            if (channels != null) {
                channels.removeIf(ch -> ch.id().asShortText().equals(channelId));
                if (channels.isEmpty()) {
                    localAgentChannels.remove(agentId);
                    assignmentService.markAgentOffline(agentId);
                    log.info("Agent {} fully unregistered (no channels left), Redis online status cleared", agentId);
                } else {
                    log.debug("Agent {} channel {} closed, {} tab(s) still open, staying online",
                            agentId, channelId, channels.size());
                }
            }
            return String.valueOf(agentId);
        }
        return null;
    }

    // ========== Channel lookups ==========

    public boolean hasUserLocally(String userId) {
        Set<Channel> channels = localUserChannels.get(userId);
        return channels != null && !channels.isEmpty();
    }

    public boolean hasAgentLocally(Long agentId) {
        Set<Channel> channels = localAgentChannels.get(agentId);
        return channels != null && !channels.isEmpty();
    }

    public String getUserIdByChannel(String channelId) {
        return channelUsers.get(channelId);
    }

    public Long getAgentIdByChannel(String channelId) {
        return channelAgents.get(channelId);
    }

    // ========== Sending messages ==========

    /**
     * Send a message JSON to all channels of an agent (broadcasts to all tabs).
     */
    private void sendToAllAgentChannels(Long agentId, String messageJson) {
        Set<Channel> channels = localAgentChannels.get(agentId);
        if (channels == null || channels.isEmpty()) return;
        for (Channel ch : channels) {
            if (ch.isActive()) {
                ch.writeAndFlush(new TextWebSocketFrame(messageJson));
            }
        }
    }

    /**
     * Send a message JSON to all channels of a user (broadcasts to all tabs).
     */
    private void sendToAllUserChannels(String userId, String messageJson) {
        Set<Channel> channels = localUserChannels.get(userId);
        if (channels == null || channels.isEmpty()) return;
        for (Channel ch : channels) {
            if (ch.isActive()) {
                ch.writeAndFlush(new TextWebSocketFrame(messageJson));
            }
        }
    }

    // ========== Cross-instance message publishing ==========

    /**
     * Publish a user message to the agent, trying local delivery first (broadcasts to all tabs).
     */
    public void publishUserMessage(Long agentId, String messageJson) {
        if (hasAgentLocally(agentId)) {
            sendToAllAgentChannels(agentId, messageJson);
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
     * Publish an agent message to the user, trying local delivery first.
     */
    public void publishAgentMessage(String userId, String messageJson) {
        if (hasUserLocally(userId)) {
            sendToAllUserChannels(userId, messageJson);
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
     * Deliver a message to all local agent channels (called from Pub/Sub listener).
     */
    public void deliverToLocalAgent(Long agentId, String messageJson) {
        sendToAllAgentChannels(agentId, messageJson);
    }

    /**
     * Deliver a message to all local user channels (called from Pub/Sub listener).
     */
    public void deliverToLocalUser(String userId, String messageJson) {
        sendToAllUserChannels(userId, messageJson);
    }

    // ========== Direct notification helpers (for WebSocketHandler) ==========

    /**
     * Send a message to the first active channel of an agent.
     * Used for one-shot notifications where broadcasting is unnecessary.
     */
    public void notifyAgentChannel(Long agentId, String messageJson) {
        sendToAllAgentChannels(agentId, messageJson);
    }

    /**
     * Send a user-offline notification to the assigned agent.
     */
    public void notifyUserOffline(Long agentId, String messageJson) {
        if (hasAgentLocally(agentId)) {
            sendToAllAgentChannels(agentId, messageJson);
        }
    }
}
