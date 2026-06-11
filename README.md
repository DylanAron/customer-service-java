# 客服系统 (Customer Service System)

> 基于 Spring Boot 3 + MyBatis-Plus + Netty + Redis + React 19 的在线客服系统，支持多实例部署、WebSocket 实时通信、客服自动分配、文件上传。

---

## 目录

- [开发环境（Windows + IDEA）](#开发环境windows--idea)
- [生产构建部署](#生产构建部署)
- [访问说明](#访问说明)
- [Nginx HTTPS 配置](#nginx-https-配置)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [默认账号](#默认账号)
- [页面入口](#页面入口)
- [API 文档](#api-文档)
- [WebSocket 协议](#websocket-协议)
- [核心功能](#核心功能)
- [文件存储](#文件存储)
- [日志](#日志)
- [常见操作](#常见操作)

---

## 开发环境（Windows + IDEA）

### 前置条件

| 依赖 | 版本要求 |
|------|----------|
| JDK | 21+ |
| MySQL | 8.0+ |
| Redis | 6.x+ |
| Node.js | 18+（前端开发用） |
| Maven | 3.8+（IDEA 自带） |

### 启动 MySQL 和 Redis

```bash
# Redis（确保本地已安装）
redis-server

# MySQL（确保 localhost:3306 已启动，数据库自动创建）
```

### 第一步：启动后端（IDEA 或命令行）

```bash
cd backend
mvn spring-boot:run
# HTTP API → http://localhost:8089
# WebSocket → ws://localhost:9090
```

> **注意**：后端项目中的 `frontend-maven-plugin` 已移到 Maven `full` profile 中，IDEA 直接运行 `mvn spring-boot:run` 不会触发前端构建，不会出现 EPERM 报错。

### 第二步：启动前端（开发模式）

```bash
cd frontend
npm install
npm run dev
# 开发服务器 → http://localhost:3000
# Vite 自动代理：
#   /api/*     → http://localhost:8089
#   /ws/*      → ws://localhost:9090
#   /uploads/* → http://localhost:8089
```

### 开发时访问

前端开发模式（localhost:3000）和后端独立运行，通过 Vite 代理通信。

---

## 生产构建部署

### Windows 打包（在开发机上执行）

```bash
# 方式一：一键打包（推荐）
./build.sh

# build.sh 会依次执行：
#   1. cd frontend && npm run build（构建前端）
#   2. 复制 dist 到 backend/src/main/resources/static/
#   3. cd backend && mvn clean package -DskipTests（打包 JAR）
# 产物：backend/target/customer-service.jar
```

```bash
# 方式二：仅后端打包（如果前端已有 dist/）
cd backend
mvn clean package -DskipTests -Dskip.npm -Dskip.installnodenpm
```

获取 `backend/target/customer-service.jar`。

### 服务器部署

服务器目录结构：

```
/opt/customer-service/
├── customer-service.jar    # 打包好的 JAR
├── start.sh                # 启动脚本
└── logs/                   # 自动创建
    ├── console.log         # 启动日志
    ├── info.log            # 应用日志（按天滚动，30天）
    ├── error.log           # 错误日志（按天滚动，60天）
    ├── gc.log              # GC 日志
    └── heapdump.hprof      # OOM 时自动堆转储
```

```bash
# 1. 上传 JAR 和启动脚本到服务器
scp backend/target/customer-service.jar user@server:/opt/customer-service/
scp start.sh user@server:/opt/customer-service/

# 2. 启动
ssh user@server
cd /opt/customer-service
chmod +x start.sh
./start.sh              # 启动（默认 prod 环境，自动写 PID 和日志）
./start.sh stop         # 优雅停止
./start.sh restart      # 重启
./start.sh status       # 查看状态
```

### 生产构建方式：Maven full profile

也可以直接激活 `full` profile 一步完成：

```bash
cd backend
mvn clean package -Pfull
# 这会自动执行：npm ci → npm run build → 复制 dist → 打包 JAR
```

---

## 访问说明

### 部署后访问

配置好 Nginx 反向代理后：

| 页面 | 地址 | 说明 |
|------|------|------|
| **用户聊天** | `https://你的域名/` | 访客直接访问，无需登录 |
| **客服登录** | `https://你的域名/agent/login` | 客服登录入口 |
| **客服工作台** | `https://你的域名/agent` | 客服接待用户 |
| **管理员登录** | `https://你的域名/admin/login` | 管理员登录入口 |
| **管理面板** | `https://你的域名/admin` | 客服管理 + 自动回复设置 + 聊天记录 |

> 前端已打包在 JAR 中，后端直接提供静态文件访问。
> 所有 `/api/` 请求和 WebSocket `/ws` 连接都由 Nginx 自动代理到后端端口。

### 后端直接访问（无 Nginx 时）

如果你直接启动 JAR 而不配置 Nginx：

```bash
java -jar customer-service.jar --spring.profiles.active=prod
# 前端页面：http://服务器IP:8080/
# API：http://服务器IP:8080/api/...
# WebSocket：ws://服务器IP:9090/ws/...
```

---

## Nginx HTTPS 配置

配置文件模板：`docs/nginx-ssl.conf`

```bash
# 1. 修改域名
sed -i 's/your-domain.com/你的域名/g' docs/nginx-ssl.conf

# 2. 放置 SSL 证书到 /etc/nginx/certs/

# 3. 部署
sudo cp docs/nginx-ssl.conf /etc/nginx/sites-available/customer-service
sudo ln -s /etc/nginx/sites-available/customer-service /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

| 项目 | 值 |
|------|-----|
| HTTPS | 443 + HTTP/2 |
| TLS | v1.2 / v1.3 |
| HTTP 80 | 强制 301 → HTTPS |
| WebSocket | `/ws` → 9090，超时 3600s |
| 文件上传大小 | 60MB |
| 前端缓存 | index.html 不缓存，assets/ 缓存 1 年 |

---

## 系统架构

```
用户浏览器 → 443 (HTTPS) → Nginx
                              ├── /api/*      → backend :8080（REST API）
                              ├── /uploads/*  → backend :8080（文件访问）
                              ├── /ws         → Netty  :9090（WebSocket）
                              └── /           → 静态页面（JAR 内）
```

| 端口 | 协议 | 用途 | 对外 |
|------|------|------|------|
| 443 | HTTPS | 前端页面 + API | ✅ |
| 80 | HTTP | 跳转 443 | ✅ |
| 8080 | HTTP | 后端 API | ❌ |
| 9090 | TCP | WebSocket | ❌ |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.3.5, Java 21 |
| 持久层 | MyBatis-Plus 3.5.9 + MySQL 8 |
| 缓存 | Redis (Lettuce) + Caffeine |
| WebSocket | Netty（独立端口 9090） |
| 认证 | JWT (Auth0 java-jwt) |
| 文件存储 | 本地文件系统 + MinIO 接口预留 |
| 前端 | React 19, Vite 8, React Router 6 |
| 构建 | Maven（`-Pfull` 全量构建） |

---

## 项目结构

```
├── start.sh                     # 生产启动/停止/重启脚本
├── build.sh                     # 全量构建脚本（前端 + JAR）
├── backend/                     # Spring Boot 3.3（Java 21 + Maven）
│   ├── src/main/java/com/customer/
│   │   ├── config/              # JWT, Redis, CORS, 数据初始化
│   │   ├── constant/            # Redis Key, WS 消息类型
│   │   ├── controller/          # REST API
│   │   ├── entity/              # MyBatis-Plus 实体
│   │   ├── enums/               # 枚举
│   │   ├── repository/          # Mapper 接口
│   │   ├── service/             # 业务逻辑
│   │   ├── storage/             # 文件存储抽象层
│   │   └── websocket/           # Netty WebSocket
│   └── src/main/resources/
│       ├── application.yml      # 开发配置
│       ├── application-prod.yml # 生产配置
│       ├── schema.sql           # DDL
│       ├── logback-spring.xml   # 日志
│       └── mapper/              # XML SQL
├── frontend/                    # React 19 + Vite
│   └── src/
│       ├── App.jsx              # 路由
│       ├── pages/               # 5 个页面
│       └── utils/api.js         # HTTP + WebSocket
├── docs/
│   ├── backend.md / frontend.md # 架构文档
│   └── nginx-ssl.conf           # Nginx 配置模板
└── logs/                        # 运行日志（自动生成）
```

---

## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 客服1 | kf1 | 123456 |
| 客服2 | kf2 | 123456 |
| 客服3 | kf3 | 123456 |

> 访客用户无需登录，直接访问首页即可匿名聊天。

---

## 页面入口

| 页面 | 路径 |
|------|------|
| 访客聊天 | `/` |
| 客服登录 | `/agent/login` |
| 客服工作台 | `/agent` |
| 管理员登录 | `/admin/login` |
| 管理面板 | `/admin` |

---

## API 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| GET | `/api/agent/list` | 客服列表 |
| POST | `/api/agent/add` | 新增客服 |
| PUT | `/api/agent/update/{id}` | 更新客服 |
| DELETE | `/api/agent/delete/{id}` | 删除客服 |
| GET | `/api/message/history/{userId}` | 消息历史 |
| GET | `/api/message/users` | 用户列表 |
| POST | `/api/message/mark-read/{userId}` | 标记已读 |
| POST | `/api/message/upload` | 文件上传 |
| GET | `/api/settings` | 获取设置 |
| PUT | `/api/settings` | 更新设置 |

---

## WebSocket 协议

| 端点 | 角色 |
|------|------|
| `ws://host:9090/ws/user/{userId}` | 用户 |
| `ws://host:9090/ws/agent/{agentId}` | 客服 |

消息类型：`user_message` / `agent_message` / `new_user` / `user_offline` / `system` / `welcome_message` / `ping` / `pong`

---

## 核心功能

### 客服分配策略

1. 检查用户已有分配且客服在线 → 复用
2. 否则从在线客服中随机选择
3. 写入 Redis（TTL 1 天）
4. 无在线客服时消息进入队列，客服上线后自动分配

### 多实例支持

| 功能 | 实现 |
|------|------|
| 分配共享 | Redis KV + Set |
| 在线状态 | Redis TTL 心跳 |
| 跨实例消息 | Redis Pub/Sub |
| 本地缓存 | Caffeine |

---

## 文件存储

```
filesUpload/
  images/2026/06/09/xxx.jpg    # 图片自动分类 + 按日分文件夹
  files/2026/06/09/xxx.pdf     # 其他文件
```

存储接口 `FileStorageService`，预留 MinIO 接入点。

---

## 日志

```
logs/
  info.log        # INFO 按天滚动 30 天
  error.log       # ERROR 按天滚动 60 天
  console.log     # 启动日志
  gc.log          # GC 日志
  heapdump.hprof  # OOM 自动转储
```

---

## 常见操作

### IDEA 开发

```bash
# 启动后端（在 IDEA Terminal 或命令行）
cd backend && mvn spring-boot:run

# 启动前端（另一个终端）
cd frontend && npm run dev
```

### 生产构建

```bash
# 方式一（推荐）
./build.sh

# 方式二（Maven profile）
cd backend && mvn clean package -Pfull

# 方式三（仅后端，已有前端 dist）
cd backend && mvn clean package -DskipTests -Dskip.npm -Dskip.installnodenpm
```

### 生产部署

```bash
# JAR + start.sh 放在同一目录
./start.sh           # 启动
./start.sh stop      # 停止
./start.sh restart   # 重启
./start.sh status    # 状态
```

### 数据库

建表脚本：`backend/src/main/resources/schema.sql`

开发环境自动执行，生产环境需手动执行。
