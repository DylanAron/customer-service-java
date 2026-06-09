package com.customer.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(Long agentId, String username) {
        return JWT.create()
                .withClaim("agentId", agentId)
                .withClaim("username", username)
                .withExpiresAt(new Date(System.currentTimeMillis() + expiration))
                .sign(Algorithm.HMAC256(secret));
    }

    public boolean validateToken(String token) {
        try {
            JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getAgentIdFromToken(String token) {
        return JWT.require(Algorithm.HMAC256(secret)).build().verify(token).getClaim("agentId").asLong();
    }

    public String getUsernameFromToken(String token) {
        return JWT.require(Algorithm.HMAC256(secret)).build().verify(token).getClaim("username").asString();
    }
}
