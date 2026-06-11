package com.customer.config;

import com.customer.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Helper for JWT-based endpoint protection.
 * Used directly in controller methods that need auth.
 */
@Component
public class AuthHelper {

    private final JwtUtil jwtUtil;

    public AuthHelper(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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
}
