package com.customer.service;

import com.customer.constant.ApiConst;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub listener for cross-instance message delivery.
 * <p>
 * When a user/agent connects to instance A but the counterpart is on instance B,
 * the message is published via Redis.  This listener receives it on instance B
 * and delivers it to the local WebSocket channel.
 * </p>
 */
@Component
public class RedisPubSubListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubListener.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final RedisWebSocketManager wsManager;

    public RedisPubSubListener(RedisWebSocketManager wsManager) {
        this.wsManager = wsManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            switch (channel) {
                case ApiConst.PUBSUB_USER_MESSAGE:
                    handleUserMessage(body);
                    break;
                case ApiConst.PUBSUB_AGENT_MESSAGE:
                    handleAgentMessage(body);
                    break;
                case ApiConst.PUBSUB_NOTIFY:
                    handleNotification(body);
                    break;
                default:
                    log.debug("Unknown Pub/Sub channel: {}", channel);
            }
        } catch (Exception e) {
            log.error("Error processing Pub/Sub message", e);
        }
    }

    /**
     * A user message was published. Check if the target agent is on this instance.
     */
    private void handleUserMessage(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            Long agentId = json.has("agentId") ? json.get("agentId").asLong() : null;
            if (agentId == null) return;
            wsManager.deliverToLocalAgent(agentId, body);
        } catch (Exception e) {
            log.error("handleUserMessage error", e);
        }
    }

    /**
     * An agent message was published. Check if the target user is on this instance.
     */
    private void handleAgentMessage(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            String userId = json.has("userId") ? json.get("userId").asText() : null;
            if (userId == null) return;
            wsManager.deliverToLocalUser(userId, body);
        } catch (Exception e) {
            log.error("handleAgentMessage error", e);
        }
    }

    /**
     * A notification (assignment change, status update) was published.
     * Currently a no-op — the assignment data lives in Redis so all instances
     * read the same state. Reserved for future cache invalidation.
     */
    private void handleNotification(String body) {
        log.debug("Received notification: {}", body);
        // Future: invalidate local caches if needed
    }
}
