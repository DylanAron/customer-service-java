package com.customer.websocket;

import com.customer.entity.Agent;
import com.customer.repository.AgentRepository;
import com.customer.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentAssignmentService {

    private final AgentRepository agentRepository;
    private final MessageRepository messageRepository;
    private final Map<String, Long> userAgentMap = new ConcurrentHashMap<>();
    private final Map<String, Long> userTodayAgentMap = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> agentUserMap = new ConcurrentHashMap<>();

    public AgentAssignmentService(AgentRepository agentRepository, MessageRepository messageRepository) {
        this.agentRepository = agentRepository;
        this.messageRepository = messageRepository;
    }

    public synchronized Long assignAgent(String userId) {
        // Rule 1: Check if this user talked to any agent today
        if (userTodayAgentMap.containsKey(userId)) {
            Long lastAgentId = userTodayAgentMap.get(userId);
            Optional<Agent> agentOpt = agentRepository.findById(lastAgentId);
            if (agentOpt.isPresent() && agentOpt.get().isOnline() && agentOpt.get().isEnabled()) {
                userAgentMap.put(userId, lastAgentId);
                agentUserMap.computeIfAbsent(lastAgentId, k -> ConcurrentHashMap.newKeySet()).add(userId);
                return lastAgentId;
            }
        }

        // Rule 2: Random online agent
        List<Agent> onlineAgents = agentRepository.findByOnlineTrue();
        List<Agent> availableAgents = onlineAgents.stream()
                .filter(Agent::isEnabled)
                .toList();

        if (!availableAgents.isEmpty()) {
            Random rand = new Random();
            Agent selected = availableAgents.get(rand.nextInt(availableAgents.size()));
            userAgentMap.put(userId, selected.getId());
            userTodayAgentMap.put(userId, selected.getId());
            agentUserMap.computeIfAbsent(selected.getId(), k -> ConcurrentHashMap.newKeySet()).add(userId);
            return selected.getId();
        }

        return null;
    }

    public Long getAssignedAgent(String userId) {
        return userAgentMap.get(userId);
    }

    public List<String> getUsersForAgent(Long agentId) {
        Set<String> users = agentUserMap.get(agentId);
        if (users == null) return List.of();
        return new ArrayList<>(users);
    }

    public void removeUser(String userId) {
        Long agentId = userAgentMap.remove(userId);
        if (agentId != null) {
            Set<String> users = agentUserMap.get(agentId);
            if (users != null) users.remove(userId);
        }
    }
}
