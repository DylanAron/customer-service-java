package com.customer.controller;

import com.customer.config.JwtUtil;
import com.customer.entity.Agent;
import com.customer.service.AgentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AgentService agentService;
    private final JwtUtil jwtUtil;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    /** IP -> timestamp of last failed attempt (simple rate limiting) */
    private final ConcurrentHashMap<String, Long> loginAttempts = new ConcurrentHashMap<>();

    public AuthController(AgentService agentService, JwtUtil jwtUtil) {
        this.agentService = agentService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        // 管理员登录（从配置读取，不再硬编码）
        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            String token = jwtUtil.generateToken(0L, "admin");
            return ResponseEntity.ok(Map.of("token", token, "role", "admin", "username", "admin"));
        }

        Agent agent = agentService.login(username, password);
        if (agent != null) {
            String token = jwtUtil.generateToken(agent.getId(), agent.getUsername());
            return ResponseEntity.ok(Map.of(
                "token", token, "role", "agent",
                "username", agent.getUsername(),
                "nickname", agent.getNickname() != null ? agent.getNickname() : agent.getUsername(),
                "agentId", agent.getId()
            ));
        }

        // 登录失败 - 清除敏感信息
        return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
    }
}
