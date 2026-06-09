package com.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.constant.ApiConst;
import com.customer.entity.Agent;
import com.customer.repository.AgentMapper;
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
    private final AgentMapper agentMapper;

    public RedisAssignmentService(RedisTemplate<String, String> redisTemplate,
                                   AgentMapper agentMapper) {
        this.redisTemplate = redisTemplate;
        this.agentMapper = agentMapper;
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
        List<Agent> onlineAgents = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>().eq(Agent::isOnline, true));
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

    public Long getAssignedAgent(String userId) {
        String val = redisTemplate.opsForValue().get(ApiConst.REDIS_KEY_ASSIGNMENT_USER + userId);
        return val != null ? Long.parseLong(val) : null;
    }

    public List<String> getUsersForAgent(Long agentId) {
        Set<String> users = redisTemplate.opsForSet().members(ApiConst.REDIS_KEY_ASSIGNMENT_AGENT + agentId);
        return users != null ? new ArrayList<>(users) : List.of();
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
                Duration.ofSeconds(ApiConst.TTL_ONLINE));
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

    public void touchUserLastVisit(String userId) {
        redisTemplate.opsForValue().set(
                ApiConst.REDIS_KEY_USER_LAST_VISIT + userId,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(ApiConst.TTL_LAST_VISIT));
    }

    public boolean hasRecentVisit(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ApiConst.REDIS_KEY_USER_LAST_VISIT + userId));
    }
}
