package com.customer.config;

import com.customer.entity.Agent;
import com.customer.repository.AgentMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Helper for JWT-based endpoint protection.
 * Used directly in controller methods that need auth.
 */
@Component
public class AuthHelper {

    private final JwtUtil jwtUtil;
    private final AgentMapper agentMapper;

    public AuthHelper(JwtUtil jwtUtil, AgentMapper agentMapper) {
        this.jwtUtil = jwtUtil;
        this.agentMapper = agentMapper;
    }

    /**
     * Validate JWT from Authorization header.
     * @return agentId (0 for admin) if valid, null if invalid/missing
     */
    public Long validateRequest(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = auth.substring(7);
            if (jwtUtil.validateToken(token)) {
                return jwtUtil.getAgentIdFromToken(token);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 管理员 token 的 agentId 固定为 0。
     */
    public boolean isAdmin(HttpServletRequest request) {
        return Long.valueOf(0L).equals(validateRequest(request));
    }

    /**
     * 校验后台身份：管理员返回 0，真实客服返回客服 id，访客 token 返回 null。
     */
    public Long validateAgentOrAdminRequest(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = auth.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return null;
            }
            Long agentId = jwtUtil.getAgentIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            if (Long.valueOf(0L).equals(agentId) && "admin".equals(username)) {
                return 0L;
            }
            Agent agent = agentMapper.selectById(agentId);
            if (agent != null && username.equals(agent.getUsername())) {
                return agentId;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
