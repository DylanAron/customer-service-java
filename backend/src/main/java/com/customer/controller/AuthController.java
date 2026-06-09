package com.customer.controller;

import com.customer.config.JwtUtil;
import com.customer.entity.Agent;
import com.customer.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AgentService agentService;
    private final JwtUtil jwtUtil;

    public AuthController(AgentService agentService, JwtUtil jwtUtil) {
        this.agentService = agentService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        if ("admin".equals(req.get("username")) && "admin123".equals(req.get("password"))) {
            String token = jwtUtil.generateToken(0L, "admin");
            return ResponseEntity.ok(Map.of("token", token, "role", "admin", "username", "admin"));
        }
        Agent agent = agentService.login(req.get("username"), req.get("password"));
        if (agent != null) {
            String token = jwtUtil.generateToken(agent.getId(), agent.getUsername());
            return ResponseEntity.ok(Map.of(
                "token", token, "role", "agent",
                "username", agent.getUsername(),
                "nickname", agent.getNickname() != null ? agent.getNickname() : agent.getUsername(),
                "agentId", agent.getId()
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
    }
}
