package com.customer.service;

import com.customer.entity.CsUser;
import com.customer.entity.Message;
import com.customer.repository.CsUserRepository;
import com.customer.repository.MessageRepository;
import com.customer.constant.ApiConst;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final CsUserRepository csUserRepository;
    private final RedisAssignmentService assignmentService;

    public MessageService(MessageRepository messageRepository, CsUserRepository csUserRepository,
                          RedisAssignmentService assignmentService) {
        this.messageRepository = messageRepository;
        this.csUserRepository = csUserRepository;
        this.assignmentService = assignmentService;
    }

    public Message saveMessage(String userId, Long agentId, String content, String msgType, String fileUrl, String direction) {
        return saveMessage(userId, agentId, content, msgType, fileUrl, direction, null);
    }

    public Message saveMessage(String userId, Long agentId, String content, String msgType, String fileUrl, String direction, String channelCode) {
        Message msg = new Message();
        msg.setUserId(userId);
        msg.setAgentId(agentId);
        msg.setContent(content);
        msg.setMsgType(msgType);
        msg.setFileUrl(fileUrl);
        msg.setDirection(direction);
        msg.setChannelCode(channelCode);
        msg.setRead(false);
        msg.setCreatedAt(LocalDateTime.now());
        messageRepository.save(msg);

        if (csUserRepository.findByUserId(userId).isEmpty()) {
            CsUser user = new CsUser();
            user.setUserId(userId);
            user.setNickname("用户" + userId.substring(Math.max(0, userId.length()-6)));
            csUserRepository.save(user);
        }
        return msg;
    }

    public List<Message> getMessages(String userId) {
        return messageRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public void markAsRead(String userId) {
        messageRepository.markAsRead(userId);
    }

    public List<Map<String, Object>> getUserList(Long agentId) {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Object[]> recentUsers = messageRepository.findRecentUserIdsWithPagination(sixMonthsAgo,
                PageRequest.of(0, 1000));

        Set<String> visibleUsers = new HashSet<>();
        if (agentId != null) {
            visibleUsers.addAll(assignmentService.getUsersForAgent(agentId));
            // Also include users whose last assigned agent matches this agent (from message history)
            for (Object[] row : recentUsers) {
                String uid = (String) row[0];
                Long assigned = assignmentService.getAssignedAgent(uid);
                if (assigned != null && assigned.equals(agentId)) {
                    visibleUsers.add(uid);
                }
            }
        } else {
            // Admin: see all
            for (Object[] row : recentUsers) {
                visibleUsers.add((String) row[0]);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : recentUsers) {
            String uid = (String) row[0];
            if (!visibleUsers.contains(uid)) continue;
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", uid);

            CsUser user = csUserRepository.findByUserId(uid).orElse(null);
            userInfo.put("nickname", user != null ? user.getNickname() : uid);
            userInfo.put("online", assignmentService.isUserOnline(uid));

            long unread = 0;
            List<Message> msgs = messageRepository.findByUserIdOrderByCreatedAtAsc(uid);
            for (int i = msgs.size() - 1; i >= 0; i--) {
                Message m = msgs.get(i);
                if ("agent".equals(m.getDirection())) break;
                if ("user".equals(m.getDirection()) && Boolean.FALSE.equals(m.isRead())) unread++;
            }
            userInfo.put("unread", unread);

            Message lastMsg = messageRepository.findTopByUserIdOrderByCreatedAtDesc(uid);
            if (lastMsg != null) {
                userInfo.put("lastMessage", lastMsg.getContent());
                userInfo.put("lastTime", lastMsg.getCreatedAt().toString());
            }
            result.add(userInfo);
        }

        result.sort((a, b) -> {
            boolean aOnline = (boolean) a.get("online");
            boolean bOnline = (boolean) b.get("online");
            if (aOnline != bOnline) return aOnline ? -1 : 1;
            long aUnread = (long) a.get("unread");
            long bUnread = (long) b.get("unread");
            return Long.compare(bUnread, aUnread);
        });

        return result;
    }

    public List<Map<String, Object>> getPaginatedUsers(int page, int size, Long agentId) {
        List<Map<String, Object>> allUsers = getUserList(agentId);
        int from = page * size;
        int to = Math.min(from + size, allUsers.size());
        if (from >= allUsers.size()) return List.of();
        return allUsers.subList(from, to);
    }

    public List<Message> getAllMessagesForAdmin() {
        return messageRepository.findAll();
    }
}
