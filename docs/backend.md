# 客服系统 — 后端架构文档

## 一、项目概述

一个基于 **Spring Boot 3.3 + Netty + Redis** 的在线客服系统后端，支持多实例部署、WebSocket 实时通信、客服自动分配、文件上传等功能。

### 技术栈

| 组件 | 选型 |
|------|------|
| 框架 | Spring Boot 3.3.5 |
| JDK | 21 |
| 数据库 | MySQL 8 (JPA + Hibernate `ddl-auto=update`) |
| 缓存 | Redis (Lettuce) + Caffeine 本地缓存 |
| WebSocket | Netty (独立端口 9090) |
| JWT | Auth0 java-jwt 4.4 |
| 构建 | Maven |

---

## 二、项目结构

```
backend/
├── pom.xml
├── src/main/java/com/customer/
│   ├── CustomerServiceApplication.java   # 入口
│   ├── config/
│   │   ├── DataInitializer.java          # 启动初始化（建表注释 + 默认账号）
│   │   ├── JwtUtil.java                  # JWT 生成与校验
│   │   ├── RedisConfig.java              # Redis 配置（Template + Pub/Sub）
│   │   └── WebConfig.java                # CORS + 静态资源映射
│   ├── constant/
│   │   ├── ApiConst.java                 # Redis Key、PubSub 频道、TTL 常量
│   │   └── WsMsgType.java               # WebSocket 消息类型常量
│   ├── controller/
│   │   ├── AuthController.java           # 客服/管理员登录
│   │   ├── AgentController.java          # CRM: 客服 CRUD
│   │   ├── UserController.java           # 用户注册/登录/信息查询
│   │   ├── MessageController.java        # 消息历史、用户列表、上传
│   │   └── SettingController.java        # 欢迎语 & 自动回复设置
│   ├── entity/
│   │   ├── Agent.java                    # cs_agent 客服账号实体
│   │   ├── CsUser.java                   # cs_user 访客用户实体
│   │   └── Message.java                  # cs_message 消息记录实体
│   ├── enums/
│   │   ├── MessageDirection.java         # 消息方向（user/agent）
│   │   └── MessageType.java              # 消息类型（text/image/file）
│   ├── repository/
│   │   ├── AgentRepository.java          # 客服数据访问
│   │   ├── CsUserRepository.java         # 用户数据访问
│   │   └── MessageRepository.java        # 消息数据访问（含未读统计）
│   ├── service/
│   │   ├── AgentService.java             # 客服登录/CRUD/在线状态
│   │   ├── MessageService.java           # 消息写入/查询/用户列表聚合
│   │   ├── RedisAssignmentService.java   # Redis 版客服分配（支持多实例）
│   │   ├── RedisPubSubListener.java      # Redis Pub/Sub 跨实例消息转发
│   │   ├── RedisWebSocketManager.java    # WebSocket 连接管理 & 跨实例发布
│   │   ├── RedisSettingService.java      # Redis 设置服务 + Caffeine 缓存
│   │   └── SettingService.java           # 内存版设置服务（当前未使用）
│   └── websocket/
│       ├── NettyWebSocketServer.java      # Netty 服务器（端口 9090）
│       ├── WebSocketHandler.java          # WS 消息处理核心
│       └── AgentAssignmentService.java    # 内存版客服分配（当前未使用）
└── src/main/resources/
    └── application.yml                   # 配置文件
```

---

## 三、数据库设计

### 3.1 cs_agent — 客服账号表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK, AUTO_INC) | 主键 |
| username | VARCHAR(255) (UNIQUE) | 登录用户名 |
| password | VARCHAR(255) | 登录密码 |
| nickname | VARCHAR(255) | 客服昵称 |
| enabled | TINYINT(1) DEFAULT 1 | 是否启用 |
| online | TINYINT(1) DEFAULT 0 | 是否在线 |
| last_login_time | DATETIME | 最后登录时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 3.2 cs_user — 访客用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK, AUTO_INC) | 主键 |
| user_id | VARCHAR(255) (UNIQUE) | 用户唯一标识（自动生成） |
| username | VARCHAR(255) (UNIQUE) | 注册用户名 |
| password | VARCHAR(255) | 登录密码 |
| nickname | VARCHAR(255) | 用户昵称 |
| avatar | VARCHAR(500) | 头像URL |
| last_active_time | DATETIME | 最后活跃时间 |
| created_at | DATETIME | 创建时间 |

### 3.3 cs_message — 消息记录表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK, AUTO_INC) | 主键 |
| user_id | VARCHAR(255) | 用户 ID |
| agent_id | BIGINT | 回复此消息的客服 ID |
| content | TEXT | 消息内容 |
| msg_type | VARCHAR(50) | 消息类型：text/image/file |
| file_url | VARCHAR(500) | 文件 URL |
| is_read | TINYINT(1) DEFAULT 0 | 是否已读 |
| channel_code | VARCHAR(20) | 渠道编码：h5/pc/app |
| direction | VARCHAR(10) | 消息方向：user/agent |
| created_at | DATETIME | 创建时间 |

---

## 四、API 接口

### 4.1 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 客服或管理员登录（admin/admin123 为管理员，其余为客服） |

### 4.2 用户端

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/register` | 注册 |
| POST | `/api/user/login` | 登录 |
| GET | `/api/user/info?userId=` | 获取用户信息 |

### 4.3 客服端

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agent/list` | 客服列表 |
| POST | `/api/agent/add` | 新增客服 |
| PUT | `/api/agent/update/{id}` | 更新客服信息 |
| DELETE | `/api/agent/delete/{id}` | 删除客服 |
| PUT | `/api/agent/password` | 修改密码（需 Bearer Token） |
| PUT | `/api/agent/nickname` | 修改昵称（需 Bearer Token） |

### 4.4 消息

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/message/history/{userId}` | 获取用户消息历史 |
| GET | `/api/message/users?page=&size=&agentId=` | 分页获取用户列表（含未读统计） |
| POST | `/api/message/mark-read/{userId}` | 标记已读 |
| GET | `/api/message/all-messages` | 管理员查看全部消息 |
| POST | `/api/message/reset` | 清空消息表 |
| POST | `/api/message/upload` | 文件/图片上传 |

### 4.5 设置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/settings` | 获取所有设置 |
| PUT | `/api/settings` | 更新设置（welcome_message/auto_reply_message） |

---

## 五、WebSocket 通信

### 5.1 连接地址

- 用户: `ws://host:9090/ws/user/{userId}`（userId 由前端自动生成，格式 `u_时间戳_随机数`）
- 客服: `ws://host:9090/ws/agent/{agentId}`

### 5.2 消息类型（WsMsgType）

| 类型 | 方向 | 说明 |
|------|------|------|
| `user_message` | 用户 → 服务端 | 用户发送消息 |
| `agent_message` | 客服 → 服务端 | 客服回复消息 |
| `new_user` | 服务端 → 客服 | 有新用户分配给该客服 |
| `user_offline` | 服务端 → 客服 | 用户离线通知 |
| `system` | 服务端 → 用户 | 系统通知（分配客服、无客服） |
| `welcome_message` | 服务端 → 用户 | 欢迎语（不存库） |
| `ping/pong` | 双向 | 心跳保持 |

### 5.3 消息 JSON 格式

```json
{
  "type": "user_message | agent_message",
  "userId": "u_xxx",
  "agentId": 1,
  "content": "消息内容",
  "msgType": "text | image | file",
  "fileUrl": "/uploads/xxx.jpg",
  "direction": "user | agent",
  "channelCode": "h5 | pc | app",
  "timestamp": "2024-01-01T12:00:00"
}
```

---

## 六、核心架构设计

### 6.1 多实例架构

```
┌─────────────┐     ┌─────────────┐
│  Instance A  │     │  Instance B  │
│  Netty:9090  │     │  Netty:9090  │
├─────────────┤     ├─────────────┤
│  Redis WS   │     │  Redis WS   │
│  Manager    │     │  Manager    │
└──────┬──────┘     └──────┬──────┘
       │                   │
       └────────┬──────────┘
                │
       ┌────────▼──────────┐
       │      Redis        │
       │  • Assignment KV  │
       │  • Online Status  │
       │  • Pub/Sub Channel│
       └───────────────────┘
```

**关键设计：**
- **客服分配**：通过 Redis `assignment:user:{userId} → agentId` 和 `assignment:agent:{agentId} → Set<userId>` 实现跨实例共享
- **在线状态**：Redis 以 2 分钟 TTL 实现心跳检测
- **跨实例消息**：消息先尝试本地 WebSocket 直连，否则通过 Redis Pub/Sub 转发到其他实例
- **本地设置缓存**：Caffeine 本地缓存（1 小时 TTL）减少 Redis 读取

### 6.2 用户连接流程

1. 用户通过 `/ws/user/{userId}` 建立 WebSocket 连接
2. 服务端检测是否为新访客 → 发送欢迎语
3. 调用 `RedisAssignmentService.assignAgent()` 分配客服：
   - 已有分配且客服在线 → 复用
   - 否则 → 随机选取在线客服
4. 有客服 → 发送 `system.agent_assigned` 和问候语
5. 无客服 → 发送 `system.no_agent`

### 6.3 消息流转

**用户发消息：**
1. 用户发送 `user_message`
2. 确定/分配客服
3. 消息入库（含 agentId 和 channelCode）
4. 如果客服在此实例 → 本地直推；否则 Redis Pub/Sub 转发
5. 消息回显给用户
6. 若没有在线客服 → 自动回复

**客服回复：**
1. 客服发送 `agent_message`
2. 校验客服是否确实被分配给该用户
3. 消息入库
4. 推送给用户（本地或跨实例）
5. 回显给客服

### 6.4 客服分配策略（Redis 版）

```
assignAgent(userId)
  ├─ 1. 检查 Redis: userId → agentId
  │    ├─ 存在且对应客服 Redis 在线 → 复用
  │    └─ 客服离线 → 删除旧分配
  ├─ 2. 从 DB 获取 online=true + enabled=true 的客服列表
  ├─ 3. 过滤出 Redis 中也标记为在线的客服
  ├─ 4. 随机选择一名客服
  └─ 5. 写入 Redis（TTL 1 天）
```

### 6.5 数据初始化（DataInitializer）

应用启动时会自动：
- 给 MySQL 表添加 COMMENT 注释
- 创建默认账号：`admin/admin123`（管理员）、`kf1~kf3/123456`（客服）

---

## 七、配置说明

### 7.1 application.yml 关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8080 | HTTP API 端口 |
| `websocket.port` | 9090 | WebSocket 端口 |
| `jwt.secret` | ... | JWT 密钥 |
| `jwt.expiration` | 86400000 | JWT 过期时间（1 天） |
| `spring.servlet.multipart.max-file-size` | 100MB | 文件上传限制 |

---

## 八、启动方式

```bash
# 1. 启动 MySQL 和 Redis
# 2. 启动后端
cd backend
mvn spring-boot:run

# 默认端口:
#   HTTP API: 8080
#   WebSocket: 9090
```
