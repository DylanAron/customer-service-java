package com.customer.websocket;

import com.customer.constant.ApiConst;
import com.customer.constant.WsMsgType;
import com.customer.entity.Agent;
import com.customer.entity.Message;
import com.customer.service.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    /** 每个 WebSocket 连接只发送一次自动回复 */
    private static final AttributeKey<Boolean> AUTO_REPLIED = AttributeKey.valueOf("autoReplied");
    private final MessageService messageService;
    private final RedisAssignmentService assignmentService;
    private final AgentService agentService;
    private final SettingService settingService;
    private final RedisWebSocketManager wsManager;

    public WebSocketHandler(MessageService messageService,
                            RedisAssignmentService assignmentService,
                            AgentService agentService,
                            SettingService settingService,
                            RedisWebSocketManager wsManager) {
        this.messageService = messageService;
        this.assignmentService = assignmentService;
        this.agentService = agentService;
        this.settingService = settingService;
        this.wsManager = wsManager;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake =
                    (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String uri = handshake.requestUri();
            String channelId = ctx.channel().id().asShortText();

            if (uri != null && uri.startsWith(ApiConst.WS_USER_PREFIX)) {
                String userId = uri.substring(ApiConst.WS_USER_PREFIX.length());
                wsManager.registerUser(userId, ctx.channel());

                // 每次建立 WebSocket 连接都发送欢迎语
                String welcomeMsg = settingService.getWelcomeMessage();
                ObjectNode welcome = mapper.createObjectNode();
                welcome.put("type", WsMsgType.WELCOME_MESSAGE);
                welcome.put("content", welcomeMsg);
                ctx.writeAndFlush(new TextWebSocketFrame(welcome.toString()));
                assignmentService.touchUserLastVisit(userId);

                Long agentId = assignmentService.assignAgent(userId);
                if (agentId != null) {
                    ObjectNode assignedMsg = mapper.createObjectNode();
                    assignedMsg.put("type", WsMsgType.SYSTEM);
                    assignedMsg.put("agent_assigned", String.valueOf(agentId));
                    ctx.writeAndFlush(new TextWebSocketFrame(assignedMsg.toString()));
                    notifyAgentNewUser(agentId, userId);
                    sendAssignedGreeting(ctx, userId, agentId);
                } else {
                    ObjectNode noAgentMsg = mapper.createObjectNode();
                    noAgentMsg.put("type", WsMsgType.SYSTEM);
                    noAgentMsg.put("no_agent", "当前没有在线客服，您可留言，我们会尽快回复您");
                    ctx.writeAndFlush(new TextWebSocketFrame(noAgentMsg.toString()));
                }
            } else if (uri != null && uri.startsWith(ApiConst.WS_AGENT_PREFIX)) {
                Long agentId = Long.parseLong(uri.substring(ApiConst.WS_AGENT_PREFIX.length()));
                wsManager.registerAgent(agentId, ctx.channel());
                if (agentService != null) {
                    agentService.setOnline(agentId, true);
                }
                // 上线时领取最多 3 个无人认领的用户
                int claimed = assignmentService.assignPendingUsers(agentId, 3);
                if (claimed > 0) {
                    log.info("Agent {} online, claimed {} pending user(s)", agentId, claimed);
                }
            }
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        try {
            JsonNode json = mapper.readTree(frame.text());
            String type = json.get("type").asText();
            String channelId = ctx.channel().id().asShortText();

            if (WsMsgType.USER_MESSAGE.equals(type)) {
                handleUserMessage(ctx, json, channelId);
            } else if (WsMsgType.AGENT_MESSAGE.equals(type)) {
                handleAgentMessage(ctx, json, channelId);
            } else if (WsMsgType.PING.equals(type)) {
                // 刷新在线状态 TTL（保持活跃）
                String cid = ctx.channel().id().asShortText();
                String userId = wsManager.getUserIdByChannel(cid);
                if (userId != null) {
                    assignmentService.refreshUserOnline(userId);
                } else {
                    Long agentId = wsManager.getAgentIdByChannel(cid);
                    if (agentId != null) {
                        assignmentService.refreshAgentOnline(agentId);
                    }
                }
                ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"pong\"}"));
            }
        } catch (Exception e) {
            System.err.println("WS message error: " + e.getMessage());
        }
    }

    private void handleUserMessage(ChannelHandlerContext ctx, JsonNode json, String channelId) {
        try {
            String userId = wsManager.getUserIdByChannel(channelId);
            if (userId == null) return;

            String content = json.get("content").asText();
            String msgType = json.has("msgType") ? json.get("msgType").asText() : "text";
            String fileUrl = json.has("fileUrl") ? json.get("fileUrl").asText() : null;
            String channelCode = json.has("channelCode") ? json.get("channelCode").asText() : null;

            // Determine which agent is/will be serving this user BEFORE saving
            Long assignedAgent = assignmentService.getAssignedAgent(userId);
            boolean wasNewlyAssigned = false;
            if (assignedAgent == null) {
                assignedAgent = assignmentService.assignAgent(userId);
                wasNewlyAssigned = true;
            }

            // Save message with agentId and channelCode for traceability
            messageService.saveMessage(userId, assignedAgent, content, msgType, fileUrl, "user", channelCode);

            ObjectNode response = mapper.createObjectNode();
            response.put("type", WsMsgType.USER_MESSAGE);
            response.put("userId", userId);
            response.put("content", content);
            response.put("msgType", msgType);
            response.put("direction", "user");
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            if (fileUrl != null) response.put("fileUrl", fileUrl);

            if (assignedAgent != null) {
                response.put("agentId", assignedAgent);

                // Newly assigned → notify agent and send greeting
                if (wasNewlyAssigned) {
                    notifyAgentNewUser(assignedAgent, userId);
                    sendAssignedGreeting(ctx, userId, assignedAgent);
                }

                // Forward to agent (broadcast to all tabs if local)
                if (wsManager.hasAgentLocally(assignedAgent)) {
                    wsManager.publishUserMessage(assignedAgent, response.toString());
                } else {
                    wsManager.publishUserMessage(assignedAgent, response.toString());
                }
            } else {
                // No online agent: auto-reply only, once per connection
                if (ctx.channel().attr(AUTO_REPLIED).compareAndSet(null, Boolean.TRUE)) {
                    String autoReply = settingService.getAutoReplyMessage();
                    ObjectNode replyMsg = mapper.createObjectNode();
                    replyMsg.put("type", WsMsgType.AGENT_MESSAGE);
                    replyMsg.put("userId", userId);
                    replyMsg.put("content", autoReply);
                    replyMsg.put("msgType", "text");
                    replyMsg.put("direction", "agent");
                    replyMsg.put("timestamp",
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    ctx.writeAndFlush(new TextWebSocketFrame(replyMsg.toString()));
                }
            }

            // Echo back to user
            ctx.writeAndFlush(new TextWebSocketFrame(response.toString()));
        } catch (Exception e) {
            System.err.println("handleUserMessage error: " + e.getMessage());
        }
    }

    private void handleAgentMessage(ChannelHandlerContext ctx, JsonNode json, String channelId) {
        try {
            Long agentId = wsManager.getAgentIdByChannel(channelId);
            if (agentId == null) return;

            String userId = json.get("userId").asText();
            String content = json.get("content").asText();
            String msgType = json.has("msgType") ? json.get("msgType").asText() : "text";
            String fileUrl = json.has("fileUrl") ? json.get("fileUrl").asText() : null;
            String channelCode = json.has("channelCode") ? json.get("channelCode").asText() : null;

            // Verify assignment
            Long assignedAgent = assignmentService.getAssignedAgent(userId);
            if (assignedAgent == null || !assignedAgent.equals(agentId)) {
                System.err.println("Agent " + agentId + " not assigned to user " + userId + ", rejecting");
                return;
            }

            Message msg = messageService.saveMessage(userId, agentId, content, msgType, fileUrl, "agent", channelCode);

            ObjectNode response = mapper.createObjectNode();
            response.put("type", WsMsgType.AGENT_MESSAGE);
            response.put("userId", userId);
            response.put("agentId", agentId);
            response.put("content", content);
            response.put("msgType", msgType);
            response.put("direction", "agent");
            response.put("timestamp", msg.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            if (fileUrl != null) response.put("fileUrl", fileUrl);

            // Deliver to user (local or cross-instance)
            wsManager.publishAgentMessage(userId, response.toString());

            // Echo to agent (broadcast to all tabs)
            ctx.writeAndFlush(new TextWebSocketFrame(response.toString()));
        } catch (Exception e) {
            System.err.println("handleAgentMessage error: " + e.getMessage());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        String userId = wsManager.getUserIdByChannel(channelId);
        if (userId != null) {
            wsManager.unregisterByChannel(channelId);
            Long assignedAgent = assignmentService.getAssignedAgent(userId);
            if (assignedAgent != null && wsManager.hasAgentLocally(assignedAgent)) {
                try {
                    ObjectNode notification = mapper.createObjectNode();
                    notification.put("type", WsMsgType.USER_OFFLINE);
                    notification.put("userId", userId);
                    wsManager.notifyUserOffline(assignedAgent, notification.toString());
                } catch (Exception ignored) {}
            }
            return;
        }

        Long agentId = wsManager.getAgentIdByChannel(channelId);
        if (agentId != null) {
            wsManager.unregisterByChannel(channelId);
            // markAgentOffline is now called INSIDE unregisterByChannel only when the LAST tab disconnects
        }
    }

    // ========== Helper methods ==========

    private void notifyAgentNewUser(Long agentId, String userId) {
        if (wsManager.hasAgentLocally(agentId)) {
            try {
                ObjectNode msg = mapper.createObjectNode();
                msg.put("type", WsMsgType.NEW_USER);
                msg.put("userId", userId);
                msg.put("content", "New user assigned to you");
                wsManager.notifyAgentChannel(agentId, msg.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendAssignedGreeting(ChannelHandlerContext ctx, String userId, Long agentId) {
        try {
            String nickName = "客服";
            Optional<Agent> agentOpt = agentService.findById(agentId);
            if (agentOpt.isPresent()) {
                nickName = agentOpt.get().getNickname();
            }
            String greeting = nickName + ",很高兴为您服务!";

            ObjectNode greetingMsg = mapper.createObjectNode();
            greetingMsg.put("type", WsMsgType.AGENT_MESSAGE);
            greetingMsg.put("userId", userId);
            greetingMsg.put("agentId", agentId);
            greetingMsg.put("content", greeting);
            greetingMsg.put("msgType", "text");
            greetingMsg.put("direction", "agent");
            greetingMsg.put("timestamp",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Deliver to user directly via the established connection
            ctx.writeAndFlush(new TextWebSocketFrame(greetingMsg.toString()));
            // Also show to agent (all tabs)
            if (wsManager.hasAgentLocally(agentId)) {
                wsManager.notifyAgentChannel(agentId, greetingMsg.toString());
            }
        } catch (Exception e) {
            System.err.println("sendAssignedGreeting error: " + e.getMessage());
        }
    }

    // Static helpers kept for backward compatibility
    public static void sendToUser(String userId, String type, String subType, String data) {}
    public static void sendToUserWithContent(String userId, String type, String content) {}
    public static void sendToAgent(Long agentId, String type, String userId, String data) {}
}
