package com.customer.controller;

import com.customer.config.JwtUtil;
import com.customer.entity.Agent;
import com.customer.service.AgentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;
    private final JwtUtil jwtUtil;

    public AgentController(AgentService agentService, JwtUtil jwtUtil) {
        this.agentService = agentService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/list")
    public ResponseEntity<List<Agent>> list() {
        return ResponseEntity.ok(agentService.getAllAgents());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        String nickname = req.get("nickname");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        Agent agent = agentService.addAgent(username, password, nickname);
        return ResponseEntity.ok(agent);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> req) {
        String nickname = req.get("nickname");
        String password = req.get("password");
        Boolean enabled = req.containsKey("enabled") ? Boolean.parseBoolean(req.get("enabled")) : null;
        Agent agent = agentService.updateAgent(id, nickname, password, enabled);
        if (agent == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(agent);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestBody Map<String, String> req,
                                             HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        String token = authHeader.substring(7);
        Long agentId = jwtUtil.getAgentIdFromToken(token);
        agentService.updatePassword(agentId, req.get("password"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/nickname")
    public ResponseEntity<?> updateNickname(@RequestBody Map<String, String> req,
                                             HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        String token = authHeader.substring(7);
        Long agentId = jwtUtil.getAgentIdFromToken(token);
        agentService.updateNickname(agentId, req.get("nickname"));
        return ResponseEntity.ok(Map.of("success", true));
    }
}
