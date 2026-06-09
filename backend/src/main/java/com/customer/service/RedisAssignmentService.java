package com.customer.service;

import com.customer.constant.ApiConst;
import com.customer.entity.Agent;
import com.customer.repository.AgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Redis-based agent-user assignment service.
 * Replaces the in-memory AgentAssignmentService to support multi-instance deployments.
 *
 * Assignment data lives in Redis so all instances see the same mapping.
 * TTL provides automatic cleanup if a user never disconnects gracefully.
 */
@Service
public class RedisAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(RedisAssignmentService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final AgentRepository agentRepository;

    public RedisAssignmentService(RedisTemplate<String, String> redisTemplate,
                                   AgentRepository agentRepository) {
        this.redisTemplate = redisTemplate;
        this.agentRepository = agentRepository;
    }

    /**
     * Assign an online agent to the given user.
     * <ol>
     *   <li>If user already has an assigned agent who is online → reuse</li>
     *   <li>Otherwise pick a random online enabled agent</li>
     * </ol>
     *
     * @param userId the user to assign
     * @return agentId, or null if no online agent available
     */
    public synchronized Long assignAgent(String userId) {
        // 1. Check existing assignment
        String existingKey = ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId;
        String agentIdStr = redisTemplate.opsForValue().get(existingKey);
        if (agentIdStr != null) {
            Long existingAgentId = Long.parseLong(agentIdStr);
            if (isAgentOnline(existingAgentId)) {
                return existingAgentId;
            }
            // assigned agent is offline → clear and reassign
            removeUser(userId);
        }

        // 2. Pick online agents from DB
        List<Agent> onlineAgents = agentRepository.findByOnlineTrue();
        List<Agent> available = onlineAgents.stream()
                .filter(Agent::isEnabled)
                .filter(a -> isAgentOnline(a.getId()))
                .toList();

        if (available.isEmpty()) {
            log.info("No online agent available for user {}, sending auto-reply", userId);
            return null;
        }

        Random rand = new Random();
        Agent selected = available.get(rand.nextInt(available.size()));
        Long agentId = selected.getId();

        // 3. Write to Redis
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId,
                String.valueOf(agentId),
                Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));
        redisTemplate.opsForSet().add(
                ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId, userId);
        redisTemplate.expire(
                ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId,
                Duration.ofSeconds(ApiConst.TTL_ASSIGNMENT));

        log.info("Assigned user {} to agent {}", userId, agentId);
        return agentId;
    }

    /**
     * Get the currently assigned agent for a user.
     *
     * @param userId the user
     * @return agentId, or null if not assigned
     */
    public Long getAssignedAgent(String userId) {
        String val = redisTemplate.opsForValue().get(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
        return val != null ? Long.parseLong(val) : null;
    }

    /**
     * Get all users assigned to a specific agent.
     *
     * @param agentId the agent
     * @return list of userIds
     */
    public List<String> getUsersForAgent(Long agentId) {
        Set<String> users = redisTemplate.opsForSet().members(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId);
        return users != null ? new ArrayList<>(users) : List.of();
    }

    /**
     * Remove assignment for a user.
     *
     * @param userId the user to remove
     */
    public void removeUser(String userId) {
        String agentIdStr = redisTemplate.opsForValue().get(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
        if (agentIdStr != null) {
            redisTemplate.opsForSet().remove(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentIdStr, userId);
        }
        redisTemplate.delete(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
    }

    /**
     * Check if an agent is online via Redis.
     *
     * @param agentId the agent
     * @return true if online
     */
    public boolean isAgentOnline(Long agentId) {
        Boolean exists = redisTemplate.hasKey(ApiConst.REDIS_KEY_AGENT_ONLINE + agentId);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Check if a user is online via Redis.
     *
     * @param userId the user
     * @return true if online
     */
    public boolean isUserOnline(String userId) {
        Boolean exists = redisTemplate.hasKey(ApiConst.REDIS_KEY_USER_ONLINE + userId);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Mark agent as online in Redis with TTL heartbeat.
     *
     * @param agentId the agent
     */
    public void markAgentOnline(Long agentId) {
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_AGENT_ONLINE + agentId,
                "1",
                Duration.ofSeconds(ApiConst.TTL_ONLINE));
    }

    /**
     * Mark user as online in Redis with TTL heartbeat.
     *
     * @param userId the user
     */
    public void markUserOnline(String userId) {
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_USER_ONLINE + userId,
                "1",
                Duration.ofSeconds(ApiConst.TTL_ONLINE));
    }

    /**
     * Remove agent online marker.
     *
     * @param agentId the agent
     */
    public void markAgentOffline(Long agentId) {
        redisTemplate.delete(ApiConst.REDIS_KEY_AGENT_ONLINE + agentId);
    }

    /**
     * Remove user online marker.
     *
     * @param userId the user
     */
    public void markUserOffline(String userId) {
        redisTemplate.delete(ApiConst.REDIS_KEY_USER_ONLINE + userId);
    }

    /**
     * Record user last visit for welcome-message dedup.
     *
     * @param userId the user
     */
    public void touchUserLastVisit(String userId) {
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_USER_LAST_VISIT + userId,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(ApiConst.TTL_LAST_VISIT));
    }

    /**
     * Check if user visited recently (within TTL_LAST_VISIT).
     *
     * @param userId the user
     * @return true if recently visited
     */
    public boolean hasRecentVisit(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ApiConst.REDIS_KEY_USER_LAST_VISIT + userId));
    }
}
