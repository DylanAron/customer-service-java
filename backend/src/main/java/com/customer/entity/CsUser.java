package com.customer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cs_user")
public class CsUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, columnDefinition = "VARCHAR(255) COMMENT '用户唯一标识（自动生成）'")
    private String userId;

    @Column(unique = true, columnDefinition = "VARCHAR(255) COMMENT '用户名（注册用户）'")
    private String username;

    @Column(nullable = false, columnDefinition = "VARCHAR(255) DEFAULT '123456' COMMENT '登录密码'")
    private String password = "123456";

    @Column(columnDefinition = "VARCHAR(255) COMMENT '用户昵称'")
    private String nickname;

    @Column(columnDefinition = "VARCHAR(500) COMMENT '头像URL'")
    private String avatar;

    @Column(name = "last_active_time", columnDefinition = "DATETIME COMMENT '最后活跃时间'")
    private LocalDateTime lastActiveTime;

    @Column(name = "created_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public LocalDateTime getLastActiveTime() { return lastActiveTime; }
    public void setLastActiveTime(LocalDateTime lastActiveTime) { this.lastActiveTime = lastActiveTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
