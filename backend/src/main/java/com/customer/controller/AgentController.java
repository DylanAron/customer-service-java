package com.customer.controller;

import com.customer.config.AuthHelper;
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
    private final AuthHelper authHelper;

    public AgentController(AgentService agentService, AuthHelper authHelper) {
        this.agentService = agentService;
        this.authHelper = authHelper;
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        return ResponseEntity.ok(agentService.getAllAgents());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody Map<String, String> req, HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
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
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, String> req, HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        String nickname = req.get("nickname");
        String password = req.get("password");
        Boolean enabled = req.containsKey("enabled") ? Boolean.parseBoolean(req.get("enabled")) : null;
        Agent agent = agentService.updateAgent(id, nickname, password, enabled);
        if (agent == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(agent);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        agentService.deleteAgent(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestBody Map<String, String> req,
                                             HttpServletRequest request) {
        Long agentId = authHelper.validateAgentOrAdminRequest(request);
        if (agentId == null || agentId.equals(0L)) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        agentService.updatePassword(agentId, req.get("password"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/nickname")
    public ResponseEntity<?> updateNickname(@RequestBody Map<String, String> req,
                                             HttpServletRequest request) {
        Long agentId = authHelper.validateAgentOrAdminRequest(request);
        if (agentId == null || agentId.equals(0L)) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        agentService.updateNickname(agentId, req.get("nickname"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile(HttpServletRequest request) {
        Long agentId = authHelper.validateAgentOrAdminRequest(request);
        if (agentId == null || agentId.equals(0L)) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        var opt = agentService.findById(agentId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Agent agent = opt.get();
        return ResponseEntity.ok(Map.of(
            "agentId", agent.getId(),
            "username", agent.getUsername(),
            "nickname", agent.getNickname() != null ? agent.getNickname() : agent.getUsername()
        ));
    }
}
