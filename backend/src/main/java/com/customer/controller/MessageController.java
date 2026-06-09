package com.customer.controller;

import com.customer.entity.Message;
import com.customer.service.MessageService;
import com.customer.service.RedisAssignmentService;
import com.customer.storage.FileInfo;
import com.customer.storage.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class MessageController {
    private final MessageService messageService;
    private final RedisAssignmentService assignmentService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;

    public MessageController(MessageService messageService,
                             RedisAssignmentService assignmentService,
                             org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                             FileStorageService fileStorageService) {
        this.messageService = messageService;
        this.assignmentService = assignmentService;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
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
            FileInfo info = fileStorageService.save(file);
            return ResponseEntity.ok(Map.of(
                "url", info.getUrl(),
                "fileName", info.getFilename()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }
}
