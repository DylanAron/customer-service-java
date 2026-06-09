package com.customer.service;

import com.customer.entity.Agent;
import com.customer.repository.AgentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class AgentService {
    private final AgentRepository agentRepository;
    private final RedisAssignmentService assignmentService;

    public AgentService(AgentRepository agentRepository, RedisAssignmentService assignmentService) {
        this.agentRepository = agentRepository;
        this.assignmentService = assignmentService;
    }

    public Agent login(String username, String password) {
        Optional<Agent> agentOpt = agentRepository.findByUsername(username);
        if (agentOpt.isPresent() && agentOpt.get().getPassword().equals(password)) {
            if (!agentOpt.get().isEnabled()) return null;
            return agentOpt.get();
        }
        return null;
    }

    public Agent addAgent(String username, String password, String nickname) {
        Agent agent = new Agent();
        agent.setUsername(username);
        agent.setPassword(password);
        agent.setNickname(nickname != null ? nickname : username);
        agent.setEnabled(true);
        return agentRepository.save(agent);
    }

    public Agent updateAgent(Long id, String nickname, String password, Boolean enabled) {
        Optional<Agent> opt = agentRepository.findById(id);
        if (opt.isPresent()) {
            Agent agent = opt.get();
            if (nickname != null) agent.setNickname(nickname);
            if (password != null) agent.setPassword(password);
            if (enabled != null) agent.setEnabled(enabled);
            return agentRepository.save(agent);
        }
        return null;
    }

    public void deleteAgent(Long id) {
        agentRepository.deleteById(id);
    }

    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    public void setOnline(Long id, boolean online) {
        agentRepository.findById(id).ifPresent(a -> {
            a.setOnline(online);
            agentRepository.save(a);
        });
        if (online) {
            assignmentService.markAgentOnline(id);
        } else {
            assignmentService.markAgentOffline(id);
        }
    }

    public Optional<Agent> findById(Long id) {
        return agentRepository.findById(id);
    }

    public long countOnline() {
        return agentRepository.countByOnlineTrue();
    }

    public void updatePassword(Long id, String newPassword) {
        agentRepository.findById(id).ifPresent(a -> {
            a.setPassword(newPassword);
            agentRepository.save(a);
        });
    }

    public void updateNickname(Long id, String nickname) {
        agentRepository.findById(id).ifPresent(a -> {
            a.setNickname(nickname);
            agentRepository.save(a);
        });
    }
}
