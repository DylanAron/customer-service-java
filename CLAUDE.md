# 客服系统 (Customer Service System)

> 基于 Spring Boot 3 + MyBatis-Plus + Netty + Redis + React 19 的在线客服系统，支持多实例部署、WebSocket 实时通信、客服自动分配。

## 项目结构

```
├── start.sh                     # 生产环境启动/停止/重启脚本（start | stop | restart | status）
├── backend/                    # Spring Boot 3.3 后端（Java 21 + Maven）
│   ├── src/main/java/com/customer/
│   │   ├── config/             # 配置类（JWT, Redis, CORS, 数据初始化）
│   │   ├── constant/           # 常量定义（Redis Key, WS 消息类型）
│   │   ├── controller/         # REST API 控制器
│   │   ├── entity/             # 实体类（MyBatis-Plus 注解）
│   │   ├── enums/              # 枚举类型
│   │   ├── repository/         # Mapper 接口（BaseMapper + XML 自定义 SQL）
│   │   ├── service/            # 业务逻辑（Redis 分配、Pub/Sub、WS 管理）
│   │   ├── storage/            # 文件存储抽象层（FileStorageService 接口 + 本地实现，预留 MinIO 接入点）
│   │   └── websocket/          # Netty WebSocket 服务器
│   └── src/main/resources/
│       ├── application.yml            # 开发配置
│       ├── application-prod.yml       # 生产配置
│       ├── schema.sql                 # DDL 建表
│       └── mapper/                    # MyBatis-Plus XML 自定义 SQL
├── frontend/                   # React 19 + Vite 前端
│   └── src/
│       ├── App.jsx             # 路由定义
│       ├── pages/              # 页面组件
│       └── utils/api.js        # HTTP + WebSocket 工具
├── filesUpload/                # 文件存储根目录（运行时自动生成）
│   ├── images/                 # 图片文件（按 yyyy/MM/dd 分日子目录）
│   │   └── 2026/06/09/        # 示例：每日目录
│   └── files/                  # 其他文件（同上的分日结构）
├── docs/                       # 文档
│   ├── backend.md
│   └── frontend.md
└── uploads/                    # （已废弃）旧文件上传目录
```
## 注释请给中文注释


## 技术栈

| 层 | 技术 |
|----|------|
| 后端框架 | Spring Boot 3.3.5, Java 21 |
| 持久层 | MyBatis-Plus 3.5.9 + MySQL 8（schema.sql 自动建表） |
| 缓存 | Redis (Lettuce) + Caffeine 本地缓存 |
| WebSocket | Netty（独立端口 9090，shutdownHook 兜底释放端口） |
| 认证 | JWT (Auth0 java-jwt) |
| 文件存储 | 本地文件系统（`filesUpload/`，图片/文件分开，按日分文件夹）+ MinIO 接口预留 |
| 优雅关闭 | `server.shutdown: graceful` + 30s 超时 |
| 前端 | React 19, Vite 8, React Router 6 |
| 构建 | Maven, npm |

## 快速启动

### 前置条件

- JDK 21+
- MySQL 8（数据库名 `customer-service`，自动创建）
- Redis（默认 localhost:6379）
- Node.js 18+

### 启动后端

**开发环境：**
```bash
cd backend
mvn spring-boot:run
# HTTP API → http://localhost:8089
# WebSocket → ws://localhost:9090
# （Ctrl+C 停止）
```

**生产环境：**
```bash
# 方式一：start.sh（推荐）
./start.sh            # 自动打包 + 启动（默认 prod 环境）
./start.sh stop       # 优雅停止
./start.sh restart    # 重启
./start.sh status     # 查看状态

# 方式二：直接 JAR
cd backend
mvn clean package -DskipTests -Dskip.npm -Dskip.installnodenpm
java -jar target/customer-service.jar --spring.profiles.active=prod

# 日志目录：项目根目录的 logs/
#   logs/console.log  启动日志
#   logs/info.log      应用日志（按天滚动，保留 30 天）
#   logs/error.log     错误日志（按天滚动，保留 60 天）
#   logs/gc.log        GC 日志
```

### 启动前端

```bash
cd frontend
npm install
npm run dev
# 开发服务器 → http://localhost:3000（自动代理 /api → 8089, /ws → 9090, /uploads → 8089）
```

### 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 客服 | kf1/kf2/kf3 | 123456 |

- **访客用户**：无需登录，直接访问 `/` 即可匿名聊天（浏览器自动生成 userId）

## 核心概念

### 三种角色

1. **访客用户** — 匿名聊天，访问 `/` 自动分配 WebSocket 连接
2. **客服** — 登录 `/agent/login` 后在工作台 `/agent` 接待用户
3. **管理员** — 登录 `/admin/login` 后在面板 `/admin` 管理客服和设置

### 消息方向

- `direction: "user"` — 用户发送的消息
- `direction: "agent"` — 客服回复的消息

### 消息类型

- `text` — 文本消息
- `image` — 图片（含 fileUrl）
- `file` — 文件（含 fileUrl）

### 客服分配策略（Redis 版）

1. 检查用户是否已有分配且客服在线 → 复用
2. 否则从在线 + 启用的客服中随机选择
3. 分配写入 Redis（TTL 1 天）
4. 分配在用户发消息时触发延迟绑定

### 多实例支持

通过 Redis 实现：
- **客服分配表**：所有实例共享 `assignment:user:{userId}` 和 `assignment:agent:{agentId}`
- **在线状态**：Redis 2 分钟 TTL 心跳
- **跨实例消息**：Redis Pub/Sub 频道 `channel:user_msg` / `channel:agent_msg` / `channel:notify`

## 数据库表

| 表名 | 说明 | 建表方式 |
|------|------|----------|
| `cs_agent` | 客服账号 | schema.sql 自动执行 |
| `cs_user` | 访客用户 | schema.sql 自动执行 |
| `cs_message` | 消息记录 | schema.sql 自动执行 |

建表脚本：`backend/src/main/resources/schema.sql`

## 文件存储

### 目录结构

```
filesUpload/                  # 项目同级目录（由 storage.local.base-path 配置）
├── images/                   # 图片文件（自动分类）
│   └── 2026/06/09/          # 按日期自动分文件夹
│       ├── a1b2c3d4.jpg
│       └── e5f6g7h8.png
└── files/                    # 非图片文件
    └── 2026/06/09/
        └── x1y2z3.pdf
```

### 存储架构

采用接口分离设计，预留 MinIO/S3 接入能力：

```
FileStorageService (接口)          ← 业务代码只依赖此接口
    ├── LocalFileStorageService    ← 当前默认实现（@Primary）
    └── MinioFileStorageService    ← 将来新增，替换 @Primary 即可
```

新增 MinIO 实现时只需：
1. 添加 `minio/java-client` 依赖
2. 新建 `MinioFileStorageService implements FileStorageService` 并加 `@Primary`
3. 配置 `storage.local.base-path` 不再需要

### 配置

```yaml
storage:
  local:
    base-path:              # 空 = <project-root>/filesUpload/
  url-prefix: /uploads      # 访问 URL 前缀
```

## API 文档

详见 [docs/backend.md](docs/backend.md)，关键端点：

| 路径 | 说明 |
|------|------|
| `POST /api/auth/login` | 登录（客服/管理员） |
| `GET /api/message/users?page=&size=&agentId=` | 客服用户列表（含未读统计） |
| `GET /api/message/history/{userId}` | 消息历史 |
| `POST /api/message/upload` | 文件上传 |
| `GET /api/settings` | 系统设置 |
| `PUT /api/settings` | 更新设置 |

## WebSocket 协议

| 端点 | 角色 |
|------|------|
| `ws://localhost:9090/ws/user/{userId}` | 用户连接 |
| `ws://localhost:9090/ws/agent/{agentId}` | 客服连接 |

消息类型：`user_message`, `agent_message`, `new_user`, `user_offline`, `system`, `welcome_message`, `ping`, `pong`

## 常见操作

### 后端

```bash
# 开发
mvn spring-boot:run                              # 启动（Ctrl+C 停止）
mvn clean compile                                # 仅编译

# 生产构建
mvn clean package -DskipTests -Dskip.npm -Dskip.installnodenpm  # 打包（跳过前端）

# 生产部署
./start.sh                                       # 启动
./start.sh stop                                  # 停止
./start.sh restart                               # 重启
./start.sh status                                # 状态

# 环境变量
SPRING_PROFILE=dev ./start.sh                    # 使用开发环境启动
JAVA_OPTS="-Xms1g -Xmx2g" ./start.sh             # 自定义 JVM 参数
```

### 前端

```bash
npm run dev                    # 开发模式
npm run build                  # 生产构建
npm run preview                # 预览生产构建
npm run lint                   # ESLint 检查
```

## 迁移记录

**Spring Data JPA → MyBatis-Plus（2026-06-09）：**
- 移除 `spring-boot-starter-data-jpa`，添加 `mybatis-plus-spring-boot3-starter:3.5.9`
- JPA 实体注解 → MyBatis-Plus 注解（`@TableName`, `@TableId`, `@TableField`）
- `JpaRepository` → `BaseMapper` + LambdaQueryWrapper
- 自定义 `@Query` → XML `MessageMapper.xml`
- DDL 由 `Hibernate ddl-auto` → `schema.sql + spring.sql.init`

**文件存储重构（2026-06-09）：**
- 上传路径从 `uploads/` 改为项目同级 `filesUpload/`
- 图片和文件分成 `images/` 和 `files/` 两个子目录
- 按 `yyyy/MM/dd` 格式自动创建每日文件夹
- 引入 `FileStorageService` 接口，预留 MinIO 接入点
- 新增生产环境配置 `application-prod.yml`
