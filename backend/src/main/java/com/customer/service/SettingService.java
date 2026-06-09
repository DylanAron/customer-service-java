package com.customer.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SettingService {

    private static final Map<String, String> settings = new ConcurrentHashMap<>();

    static {
        settings.put("welcome_message", "您好，欢迎回来！有什么可以帮您的吗？");
        settings.put("auto_reply_message", "当前没有在线客服，请留言，我们会尽快回复您。");
    }

    public String getWelcomeMessage() {
        return settings.getOrDefault("welcome_message", "您好，欢迎回来！有什么可以帮您的吗？");
    }

    public String getAutoReplyMessage() {
        return settings.getOrDefault("auto_reply_message", "当前没有在线客服，请留言，我们会尽快回复您。");
    }

    public void setWelcomeMessage(String msg) {
        settings.put("welcome_message", msg);
    }

    public void setAutoReplyMessage(String msg) {
        settings.put("auto_reply_message", msg);
    }

    public Map<String, String> getAll() {
        return Map.of(
            "welcome_message", getWelcomeMessage(),
            "auto_reply_message", getAutoReplyMessage()
        );
    }
}
