package com.customer.controller;

import com.customer.service.SettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getAll() {
        return ResponseEntity.ok(settingService.getAll());
    }

    @PutMapping
    public ResponseEntity<?> update(@RequestBody Map<String, String> body) {
        if (body.containsKey("welcome_message")) {
            settingService.setWelcomeMessage(body.get("welcome_message"));
        }
        if (body.containsKey("auto_reply_message")) {
            settingService.setAutoReplyMessage(body.get("auto_reply_message"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
