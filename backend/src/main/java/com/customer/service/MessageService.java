package com.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.entity.CsUser;
import com.customer.entity.Message;
import com.customer.repository.CsUserMapper;
import com.customer.repository.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MessageService {
    private final MessageMapper messageMapper;
    private final CsUserMapper csUserMapper;
    private final RedisAssignmentService assignmentService;

    public MessageService(MessageMapper messageMapper, CsUserMapper csUserMapper,
                          RedisAssignmentService assignmentService) {
        this.messageMapper = messageMapper;
        this.csUserMapper = csUserMapper;
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
        messageMapper.insert(msg);

        CsUser existing = csUserMapper.selectOne(
                new LambdaQueryWrapper<CsUser>().eq(CsUser::getUserId, userId));
        if (existing == null) {
            CsUser user = new CsUser();
            user.setUserId(userId);
            user.setNickname("用户" + userId.substring(Math.max(0, userId.length()-6)));
            csUserMapper.insert(user);
        }
        return msg;
    }

    public List<Message> getMessages(String userId, Long agentId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId);
        if (agentId != null) {
            wrapper.eq(Message::getAgentId, agentId);
        }
        return messageMapper.selectList(wrapper.orderByAsc(Message::getCreatedAt));
    }

    public void markAsRead(String userId) {
        messageMapper.markAsRead(userId);
    }

    public void markAsReadByAgent(String userId, Long agentId) {
        messageMapper.markAsReadByAgent(userId, agentId);
    }

    public List<Map<String, Object>> getUserList(Long agentId) {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);

        // 查与该客服聊过天的用户（messages 中有 agent_id = agentId 记录）
        List<Map<String, Object>> recentUsers;
        if (agentId != null) {
            recentUsers = messageMapper.findRecentUserIdsByAgent(agentId, sixMonthsAgo, 1000);
        } else {
            recentUsers = messageMapper.findRecentUserIdsWithPagination(sixMonthsAgo, 1000);
        }

        Set<String> visibleUsers = new HashSet<>();
        for (Map<String, Object> row : recentUsers) {
            visibleUsers.add((String) row.get("userId"));
        }

        // 加上 Redis 中分配给该客服的用户（可能刚分配还没发消息）
        if (agentId != null) {
            visibleUsers.addAll(assignmentService.getUsersForAgent(agentId));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : recentUsers) {
            String uid = (String) row.get("userId");
            if (!visibleUsers.contains(uid)) continue;
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", uid);

            CsUser user = csUserMapper.selectOne(
                    new LambdaQueryWrapper<CsUser>().eq(CsUser::getUserId, uid));
            userInfo.put("nickname", user != null ? user.getNickname() : uid);
            userInfo.put("online", assignmentService.isUserOnline(uid));

            // unread: 只看未读的用户消息（不管哪个客服发的）
            long unread = 0;
            List<Message> msgs = messageMapper.selectList(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getUserId, uid)
                            .orderByAsc(Message::getCreatedAt));
            for (int i = msgs.size() - 1; i >= 0; i--) {
                Message m = msgs.get(i);
                if ("agent".equals(m.getDirection())) break;
                if ("user".equals(m.getDirection()) && Boolean.FALSE.equals(m.isRead())) unread++;
            }
            userInfo.put("unread", unread);

            // 最新一条消息（不管哪个客服的）
            Message lastMsg = messageMapper.selectOne(
                    new LambdaQueryWrapper<Message>()
                            .eq(Message::getUserId, uid)
                            .orderByDesc(Message::getCreatedAt)
                            .last("LIMIT 1"));
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
        return messageMapper.selectList(null);
    }
}
