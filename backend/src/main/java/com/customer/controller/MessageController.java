package com.customer.controller;

import com.customer.config.AuthHelper;
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
import java.util.Set;

@RestController
@RequestMapping("/api/message")
public class MessageController {
    private final MessageService messageService;
    private final RedisAssignmentService assignmentService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;
    private final AuthHelper authHelper;

    private static final long APP_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long AGENT_MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    public MessageController(MessageService messageService,
                             RedisAssignmentService assignmentService,
                             org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                             FileStorageService fileStorageService,
                             AuthHelper authHelper) {
        this.messageService = messageService;
        this.assignmentService = assignmentService;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
        this.authHelper = authHelper;
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Message>> getHistory(
            @PathVariable String userId,
            @RequestParam(required = false) Long agentId) {
        return ResponseEntity.ok(messageService.getMessages(userId, agentId));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) Long agentId,
            HttpServletRequest request) {
        if (authHelper.validateRequest(request) == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        return ResponseEntity.ok(messageService.getPaginatedUsers(page, size, agentId));
    }

    @PostMapping("/mark-read/{userId}")
    public ResponseEntity<?> markAsRead(
            @PathVariable String userId,
            @RequestParam(required = false) Long agentId,
            HttpServletRequest request) {
        if (authHelper.validateRequest(request) == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        if (agentId != null) {
            messageService.markAsReadByAgent(userId, agentId);
        } else {
            messageService.markAsRead(userId);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/all-messages")
    public ResponseEntity<?> getAllMessages(HttpServletRequest request) {
        Long agentId = authHelper.validateRequest(request);
        if (agentId == null || agentId != 0L) {
            return ResponseEntity.status(401).body(Map.of("error", "仅管理员可操作"));
        }
        return ResponseEntity.ok(messageService.getAllMessagesForAdmin());
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetMessages(HttpServletRequest request) {
        Long agentId = authHelper.validateRequest(request);
        if (agentId == null || agentId != 0L) {
            return ResponseEntity.status(401).body(Map.of("error", "仅管理员可操作"));
        }
        jdbcTemplate.execute("TRUNCATE TABLE cs_message");
        return ResponseEntity.ok(Map.of("success", true, "message", "消息数据已清空"));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }

        // 通过 JWT token 判断是 agent 上传还是 app 用户上传
        Long agentId = authHelper.validateRequest(request);
        boolean isAgentUpload = agentId != null;

        if (isAgentUpload) {
            // agent：100MB，不限文件类型
            if (file.getSize() > AGENT_MAX_FILE_SIZE) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件大小不能超过 100MB"));
            }
        } else {
            // app 用户：10MB，仅图片
            if (file.getSize() > APP_MAX_FILE_SIZE) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件大小不能超过 10MB"));
            }
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持 JPG/PNG/GIF/WebP 格式图片"));
            }
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
