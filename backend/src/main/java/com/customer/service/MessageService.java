package com.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.entity.Agent;
import com.customer.entity.CsUser;
import com.customer.entity.Message;
import com.customer.repository.AgentMapper;
import com.customer.repository.CsUserMapper;
import com.customer.repository.MessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MessageService {
    private final MessageMapper messageMapper;
    private final CsUserMapper csUserMapper;
    private final AgentMapper agentMapper;
    private final RedisAssignmentService assignmentService;

    public MessageService(MessageMapper messageMapper, CsUserMapper csUserMapper,
                          AgentMapper agentMapper,
                          RedisAssignmentService assignmentService) {
        this.messageMapper = messageMapper;
        this.csUserMapper = csUserMapper;
        this.agentMapper = agentMapper;
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

    public List<Message> getMessages(String userId) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .orderByAsc(Message::getCreatedAt));
    }

    /**
     * app 用户按 userId + agentId 获取往来消息（不涉及客服分配逻辑），分页
     */
    public List<Message> getMessagesByAgent(String userId, Long agentId, int size, Long beforeId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getAgentId, agentId);

        if (beforeId != null) {
            wrapper.lt(Message::getId, beforeId);
        }

        // 取最新 N 条（倒序），再反转为正序供前端显示
        List<Message> messages = messageMapper.selectList(wrapper
                .orderByDesc(Message::getId)
                .last("LIMIT " + size));

        Collections.reverse(messages);
        return messages;
    }

    public List<Message> getMessagesForAgent(String userId, Long agentId) {
        Long assignedAgentId = assignmentService.getAssignedAgent(userId);
        boolean assignedToCurrentAgent = agentId.equals(assignedAgentId);
        boolean hasAgentHistory = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getAgentId, agentId)) > 0;

        if (!assignedToCurrentAgent && !hasAgentHistory) {
            return List.of();
        }

        if (assignedToCurrentAgent) {
            // 认领后补齐未归属消息，保证客服打开用户时可以看到完整上下文。
            messageMapper.assignUnassignedMessagesToAgent(userId, agentId);
        }

        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getAgentId, agentId)
                .orderByAsc(Message::getCreatedAt));
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

        Set<String> visibleUsers = new LinkedHashSet<>();
        for (Map<String, Object> row : recentUsers) {
            visibleUsers.add((String) row.get("userId"));
        }

        // 加上 Redis 中分配给该客服的用户（可能刚分配还没发消息）
        if (agentId != null) {
            visibleUsers.addAll(assignmentService.getUsersForAgent(agentId));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String uid : visibleUsers) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", uid);

            CsUser user = csUserMapper.selectOne(
                    new LambdaQueryWrapper<CsUser>().eq(CsUser::getUserId, uid));
            userInfo.put("nickname", user != null ? user.getNickname() : uid);
            userInfo.put("online", assignmentService.isUserOnline(uid));

            // unread: 只看该客服名下未读的用户消息
            long unread = 0;
            LambdaQueryWrapper<Message> unreadWrapper = new LambdaQueryWrapper<Message>()
                    .eq(Message::getUserId, uid);
            if (agentId != null) {
                unreadWrapper.eq(Message::getAgentId, agentId);
            }
            List<Message> msgs = messageMapper.selectList(
                    unreadWrapper.orderByAsc(Message::getCreatedAt));
            for (int i = msgs.size() - 1; i >= 0; i--) {
                Message m = msgs.get(i);
                if ("agent".equals(m.getDirection())) break;
                if ("user".equals(m.getDirection()) && Boolean.FALSE.equals(m.isRead())) unread++;
            }
            userInfo.put("unread", unread);

            // 该客服名下最新一条消息
            LambdaQueryWrapper<Message> lastMsgWrapper = new LambdaQueryWrapper<Message>()
                    .eq(Message::getUserId, uid);
            if (agentId != null) {
                lastMsgWrapper.eq(Message::getAgentId, agentId);
            }
            Message lastMsg = messageMapper.selectOne(
                    lastMsgWrapper.orderByDesc(Message::getCreatedAt).last("LIMIT 1"));
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

    /**
     * 查询用户未读的客服消息数量（用户视角），以及最近发送消息的客服信息。
     * 用于 app 推送通知。
     */
    public Map<String, Object> getUnreadInfo(String userId, Long afterId) {
        int count = messageMapper.countAgentMessagesAfter(userId, afterId);
        Map<String, Object> info = new HashMap<>();
        info.put("count", count);
        info.put("afterId", afterId);

        // 获取最近发送消息的客服信息
        Map<String, Object> latestAgent = messageMapper.findLatestAgentMessage(userId);
        if (latestAgent != null && latestAgent.get("agentId") != null) {
            info.put("latestAgentId", latestAgent.get("agentId"));
            Long agentId = ((Number) latestAgent.get("agentId")).longValue();
            Agent agent = agentMapper.selectById(agentId);
            info.put("latestAgentName", agent != null ? agent.getNickname() : "客服");
        } else {
            info.put("latestAgentId", null);
            info.put("latestAgentName", null);
        }

        return info;
    }

    /**
     * 记录用户最后看到的消息 ID（存入 Redis，用于推送通知的增量查询基准），
     * 同时将数据库中该用户接收的指定客服消息标记为已读。
     */
    public void markUserRead(String userId, Long lastReadMsgId, Long agentId) {
        if (lastReadMsgId == null) return;
        // 使用 Redis 存储，key: user:lastread:{userId}, value: msgId, TTL: 7 天
        String key = "user:lastread:" + userId;
        assignmentService.getRedisTemplate().opsForValue().set(
                key, String.valueOf(lastReadMsgId),
                java.time.Duration.ofDays(7));
        // 同步更新数据库已读状态：将该用户下、指定客服的消息标为已读
        messageMapper.markAgentMessagesReadUpTo(userId, lastReadMsgId, agentId);
    }
}
