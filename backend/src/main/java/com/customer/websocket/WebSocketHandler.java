package com.customer.websocket;

import com.customer.constant.ApiConst;
import com.customer.constant.WsMsgType;
import com.customer.entity.Agent;
import com.customer.entity.Message;
import com.customer.service.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final MessageService messageService;
    private final RedisAssignmentService assignmentService;
    private final AgentService agentService;
    private final RedisSettingService settingService;
    private final RedisWebSocketManager wsManager;

    public WebSocketHandler(MessageService messageService,
                            RedisAssignmentService assignmentService,
                            AgentService agentService,
                            RedisSettingService settingService,
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

                // 欢迎语（不存数据库）
                if (!assignmentService.hasRecentVisit(userId)) {
                    String welcomeMsg = settingService.getWelcomeMessage();
                    sendToUserWithContent(userId, WsMsgType.WELCOME_MESSAGE, welcomeMsg);
                }
                assignmentService.touchUserLastVisit(userId);

                Long agentId = assignmentService.assignAgent(userId);
                if (agentId != null) {
                    sendToUser(userId, WsMsgType.SYSTEM, "agent_assigned", String.valueOf(agentId));
                    notifyAgentNewUser(agentId, userId);
                    sendAssignedGreeting(userId, agentId);
                } else {
                    sendToUser(userId, WsMsgType.SYSTEM, "no_agent",
                            "当前没有在线客服，您可留言，我们会尽快回复您");
                }
            } else if (uri != null && uri.startsWith(ApiConst.WS_AGENT_PREFIX)) {
                Long agentId = Long.parseLong(uri.substring(ApiConst.WS_AGENT_PREFIX.length()));
                wsManager.registerAgent(agentId, ctx.channel());
                if (agentService != null) {
                    agentService.setOnline(agentId, true);
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
                    sendAssignedGreeting(userId, assignedAgent);
                }

                if (wsManager.hasAgentLocally(assignedAgent)) {
                    wsManager.getAgentChannel(assignedAgent)
                            .writeAndFlush(new TextWebSocketFrame(response.toString()));
                } else {
                    wsManager.publishUserMessage(assignedAgent, response.toString());
                }
            } else {
                // No online agent: auto-reply only, no agent to forward to
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

            // Echo to agent
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
                    wsManager.getAgentChannel(assignedAgent)
                            .writeAndFlush(new TextWebSocketFrame(notification.toString()));
                } catch (Exception ignored) {}
            }
            return;
        }

        Long agentId = wsManager.getAgentIdByChannel(channelId);
        if (agentId != null) {
            wsManager.unregisterByChannel(channelId);
            if (agentService != null) {
                agentService.setOnline(agentId, false);
            }
        }
    }

    // ========== Helper methods ==========

    private void notifyAgentNewUser(Long agentId, String userId) {
        if (wsManager.hasAgentLocally(agentId)) {
            Channel ch = wsManager.getAgentChannel(agentId);
            if (ch != null && ch.isActive()) {
                try {
                    ObjectNode msg = mapper.createObjectNode();
                    msg.put("type", WsMsgType.NEW_USER);
                    msg.put("userId", userId);
                    msg.put("content", "New user assigned to you");
                    ch.writeAndFlush(new TextWebSocketFrame(msg.toString()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendAssignedGreeting(String userId, Long agentId) {
        try {
            String agentNickname = "客服";
            Optional<Agent> agentOpt = agentService.findById(agentId);
            if (agentOpt.isPresent()) {
                agentNickname = agentOpt.get().getNickname();
            }
            String greeting = agentNickname + "很高兴为您服务!";

            ObjectNode greetingMsg = mapper.createObjectNode();
            greetingMsg.put("type", WsMsgType.AGENT_MESSAGE);
            greetingMsg.put("userId", userId);
            greetingMsg.put("agentId", agentId);
            greetingMsg.put("content", greeting);
            greetingMsg.put("msgType", "text");
            greetingMsg.put("direction", "agent");
            greetingMsg.put("timestamp",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Deliver to user (no DB persistence — ephemeral greeting only)
            wsManager.publishAgentMessage(userId, greetingMsg.toString());
            // Also show to agent
            if (wsManager.hasAgentLocally(agentId)) {
                wsManager.getAgentChannel(agentId)
                        .writeAndFlush(new TextWebSocketFrame(greetingMsg.toString()));
            }
        } catch (Exception e) {
            System.err.println("sendAssignedGreeting error: " + e.getMessage());
        }
    }

    public static void sendToUser(String userId, String type, String subType, String data) {
        // Static helper kept for backward compatibility — uses WebSocketHandler static refs are removed
        // Now this is handled through RedisWebSocketManager
    }

    public static void sendToUserWithContent(String userId, String type, String content) {
        // Static helper kept for backward compatibility
    }

    public static void sendToAgent(Long agentId, String type, String userId, String data) {
        // Static helper kept for backward compatibility
    }
}
