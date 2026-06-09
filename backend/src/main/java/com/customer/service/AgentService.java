package com.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.entity.Agent;
import com.customer.repository.AgentMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class AgentService {
    private final AgentMapper agentMapper;
    private final RedisAssignmentService assignmentService;

    public AgentService(AgentMapper agentMapper, RedisAssignmentService assignmentService) {
        this.agentMapper = agentMapper;
        this.assignmentService = assignmentService;
    }

    public Agent login(String username, String password) {
        Agent agent = agentMapper.selectOne(
                new LambdaQueryWrapper<Agent>().eq(Agent::getUsername, username));
        if (agent != null && agent.getPassword().equals(password)) {
            if (!agent.isEnabled()) return null;
            return agent;
        }
        return null;
    }

    public Agent addAgent(String username, String password, String nickname) {
        Agent agent = new Agent();
        agent.setUsername(username);
        agent.setPassword(password);
        agent.setNickname(nickname != null ? nickname : username);
        agent.setEnabled(true);
        agentMapper.insert(agent);
        return agent;
    }

    public Agent updateAgent(Long id, String nickname, String password, Boolean enabled) {
        Agent agent = agentMapper.selectById(id);
        if (agent != null) {
            if (nickname != null) agent.setNickname(nickname);
            if (password != null) agent.setPassword(password);
            if (enabled != null) agent.setEnabled(enabled);
            agentMapper.updateById(agent);
            return agent;
        }
        return null;
    }

    public void deleteAgent(Long id) {
        agentMapper.deleteById(id);
    }

    public List<Agent> getAllAgents() {
        return agentMapper.selectList(null);
    }

    public void setOnline(Long id, boolean online) {
        Agent agent = agentMapper.selectById(id);
        if (agent != null) {
            agent.setOnline(online);
            agentMapper.updateById(agent);
        }
        if (online) {
            assignmentService.markAgentOnline(id);
        } else {
            assignmentService.markAgentOffline(id);
        }
    }

    public Optional<Agent> findById(Long id) {
        return Optional.ofNullable(agentMapper.selectById(id));
    }

    public long countOnline() {
        return agentMapper.selectCount(
                new LambdaQueryWrapper<Agent>().eq(Agent::isOnline, true));
    }

    public void updatePassword(Long id, String newPassword) {
        Agent agent = agentMapper.selectById(id);
        if (agent != null) {
            agent.setPassword(newPassword);
            agentMapper.updateById(agent);
        }
    }

    public void updateNickname(Long id, String nickname) {
        Agent agent = agentMapper.selectById(id);
        if (agent != null) {
            agent.setNickname(nickname);
            agentMapper.updateById(agent);
        }
    }
}
