package com.customer.controller;

import com.customer.config.AuthHelper;
import com.customer.entity.AutoReplyRule;
import com.customer.service.AutoReplyRuleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auto-reply-rules")
public class AutoReplyRuleController {

    private final AutoReplyRuleService ruleService;
    private final AuthHelper authHelper;

    public AutoReplyRuleController(AutoReplyRuleService ruleService, AuthHelper authHelper) {
        this.ruleService = ruleService;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        return ResponseEntity.ok(ruleService.listAll());
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody AutoReplyRule rule, HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        return ResponseEntity.ok(ruleService.add(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody AutoReplyRule rule,
                                    HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        AutoReplyRule updated = ruleService.update(id, rule);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        ruleService.delete(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/reload")
    public ResponseEntity<?> reload(HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        ruleService.reloadRules();
        return ResponseEntity.ok(Map.of("success", true));
    }
}
