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

    /**
     * 获取用户未读消息信息（app 端推送通知用，无需认证）。
     * afterId 参数用于增量查询——前端传入上一次看到的消息ID。
     */
    @GetMapping("/unread-count/{userId}")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") Long afterId) {
        return ResponseEntity.ok(messageService.getUnreadInfo(userId, afterId));
    }

    /**
     * App 用户标记消息已读（无需认证）。
     * 前端进入聊天页后调用，记录用户最后看到的消息 ID。
     */
    @PostMapping("/mark-user-read/{userId}")
    public ResponseEntity<Map<String, Object>> markUserRead(
            @PathVariable String userId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long lastReadMsgId = null;
        Long agentId = null;
        if (body != null) {
            if (body.get("lastReadMsgId") != null) {
                lastReadMsgId = ((Number) body.get("lastReadMsgId")).longValue();
            }
            if (body.get("agentId") != null) {
                agentId = ((Number) body.get("agentId")).longValue();
            }
        }
        messageService.markUserRead(userId, lastReadMsgId, agentId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Message>> getHistory(
            @PathVariable String userId,
            @RequestParam(required = false) Long agentId,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long beforeId,
            HttpServletRequest request) {
        if (agentId != null) {
            // app 用户携带 userId + agentId 查看聊天记录，不需要鉴权，只返回该客服的消息
            Long requesterAgentId = authHelper.validateAgentOrAdminRequest(request);
            if (requesterAgentId != null && (requesterAgentId.equals(agentId) || requesterAgentId.equals(0L))) {
                // 客服或管理员：走完整分配逻辑
                return ResponseEntity.ok(messageService.getMessagesForAgent(userId, agentId));
            }
            // app 用户：按 userId + agentId 过滤，分页返回该客服的消息
            return ResponseEntity.ok(messageService.getMessagesByAgent(userId, agentId, size, beforeId));
        }
        return ResponseEntity.ok(messageService.getMessages(userId));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(required = false) Long agentId,
            HttpServletRequest request) {
        Long requesterAgentId = authHelper.validateAgentOrAdminRequest(request);
        if (requesterAgentId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        if (agentId == null && !requesterAgentId.equals(0L)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可查看全部用户"));
        }
        if (agentId != null && !requesterAgentId.equals(agentId) && !requesterAgentId.equals(0L)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权查看其他客服的用户"));
        }
        return ResponseEntity.ok(messageService.getUserList(agentId));
    }

    @PostMapping("/mark-read/{userId}")
    public ResponseEntity<?> markAsRead(
            @PathVariable String userId,
            @RequestParam(required = false) Long agentId,
            HttpServletRequest request) {
        Long requesterAgentId = authHelper.validateAgentOrAdminRequest(request);
        if (requesterAgentId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未授权"));
        }
        if (agentId == null && !requesterAgentId.equals(0L)) {
            return ResponseEntity.status(403).body(Map.of("error", "仅管理员可执行全量已读"));
        }
        if (agentId != null && !requesterAgentId.equals(agentId) && !requesterAgentId.equals(0L)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作其他客服的消息"));
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
        Long agentId = authHelper.validateAgentOrAdminRequest(request);
        if (agentId == null || agentId != 0L) {
            return ResponseEntity.status(401).body(Map.of("error", "仅管理员可操作"));
        }
        return ResponseEntity.ok(messageService.getAllMessagesForAdmin());
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetMessages(HttpServletRequest request) {
        Long agentId = authHelper.validateAgentOrAdminRequest(request);
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
        Long agentId = authHelper.validateAgentOrAdminRequest(request);
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
