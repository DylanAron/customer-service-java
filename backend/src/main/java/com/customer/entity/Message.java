package com.customer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cs_message")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "VARCHAR(255) COMMENT '用户ID'")
    private String userId;

    @Column(columnDefinition = "BIGINT COMMENT '回复此消息的客服ID'")
    private Long agentId;

    @Column(nullable = false, columnDefinition = "TEXT COMMENT '消息内容'")
    private String content;

    @Column(columnDefinition = "VARCHAR(50) COMMENT '消息类型：text/image/file'")
    private String msgType;

    @Column(columnDefinition = "VARCHAR(500) COMMENT '文件URL（图片/文件时使用）'")
    private String fileUrl;

    @Column(name = "channel_code", columnDefinition = "VARCHAR(20) COMMENT '渠道编码：h5/pc/app/...'")
    private String channelCode;

    @Column(name = "is_read", columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否已读'")
    private Boolean isRead = false;

    @Column(nullable = false, columnDefinition = "VARCHAR(10) COMMENT '消息方向：user=用户发送, agent=客服发送'")
    private String direction;

    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMsgType() { return msgType; }
    public void setMsgType(String msgType) { this.msgType = msgType; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Boolean isRead() { return isRead; }
    public void setRead(Boolean read) { isRead = read; }
    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
