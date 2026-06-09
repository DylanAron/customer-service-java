package com.customer.controller;

import com.customer.entity.Message;
import com.customer.service.MessageService;
import com.customer.service.RedisAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/message")
public class MessageController {
    private final MessageService messageService;
    private final RedisAssignmentService assignmentService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public MessageController(MessageService messageService,
                             RedisAssignmentService assignmentService,
                             org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.messageService = messageService;
        this.assignmentService = assignmentService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Message>> getHistory(
            @PathVariable String userId,
            @RequestParam(required = false) Long agentId) {
        if (agentId != null && !agentId.equals(assignmentService.getAssignedAgent(userId))) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(messageService.getMessages(userId));
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) Long agentId) {
        return ResponseEntity.ok(messageService.getPaginatedUsers(page, size, agentId));
    }

    @PostMapping("/mark-read/{userId}")
    public ResponseEntity<?> markAsRead(
            @PathVariable String userId,
            @RequestParam(required = false) Long agentId) {
        if (agentId != null && !agentId.equals(assignmentService.getAssignedAgent(userId))) {
            return ResponseEntity.ok(Map.of("success", false, "error", "not assigned"));
        }
        messageService.markAsRead(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/all-messages")
    public ResponseEntity<List<Message>> getAllMessages() {
        return ResponseEntity.ok(messageService.getAllMessagesForAdmin());
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetMessages() {
        jdbcTemplate.execute("TRUNCATE TABLE cs_message");
        return ResponseEntity.ok(Map.of("success", true, "message", "消息数据已清空"));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }
        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String ext = "";
            String originalName = file.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + ext;
            File dest = new File(uploadDir + fileName);
            file.transferTo(dest);

            String url = "/uploads/" + fileName;
            System.out.println("File uploaded: " + dest.getAbsolutePath());
            return ResponseEntity.ok(Map.of("url", url, "fileName", originalName));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }
}
