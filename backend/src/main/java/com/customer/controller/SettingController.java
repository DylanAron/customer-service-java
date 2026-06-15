package com.customer.controller;

import com.customer.config.AuthHelper;
import com.customer.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settingService;
    private final AuthHelper authHelper;

    public SettingController(SettingService settingService, AuthHelper authHelper) {
        this.settingService = settingService;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getAll() {
        return ResponseEntity.ok(settingService.getAll());
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!authHelper.isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        }
        if (body.containsKey("welcome_message")) {
            settingService.setWelcomeMessage(body.get("welcome_message"));
        }
        if (body.containsKey("auto_reply_message")) {
            settingService.setAutoReplyMessage(body.get("auto_reply_message"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
