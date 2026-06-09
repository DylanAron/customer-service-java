# 客服系统 — 前端架构文档

## 一、项目概述

基于 **React 19 + Vite 8 + React Router 6** 构建的在线客服系统前端，提供访客端即时聊天、客服工作台和管理面板三大界面。

### 技术栈

| 组件 | 选型 |
|------|------|
| 框架 | React 19 |
| 构建 | Vite 8 |
| 路由 | react-router-dom 6 |
| HTTP | Fetch API (封装在 `api.js`) |
| WebSocket | 原生 WebSocket (封装在 `api.js`) |
| 样式 | Inline Styles (无 CSS 框架) |

---

## 二、项目结构

```
frontend/
├── index.html                  # 入口 HTML
├── package.json                # 依赖配置
├── vite.config.js              # Vite 配置（代理 /api → 8080, /ws → 9090）
├── eslint.config.js
├── public/
│   ├── favicon.svg
│   └── icons.svg
└── src/
    ├── main.jsx                # 渲染入口（BrowserRouter 包裹）
    ├── App.jsx                 # 路由定义
    ├── utils/
    │   └── api.js              # HTTP + WebSocket 工具函数
    └── pages/
        ├── UserChat.jsx        # 用户聊天页面 (/)
        ├── UserLogin.jsx       # 用户登录/注册页 (未使用)
        ├── AgentLogin.jsx      # 客服登录页 (/agent/login)
        ├── AgentPanel.jsx      # 客服工作台 (/agent)
        ├── AdminLogin.jsx      # 管理员登录页 (/admin/login)
        └── AdminPanel.jsx      # 管理面板 (/admin)
```

---

## 三、路由设计

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` | `UserChat` | **访客聊天页** — 匿名用户直接对话 |
| `/agent/login` | `AgentLogin` | 客服登录入口 |
| `/agent` | `AgentPanel` | 客服工作台（需登录） |
| `/admin/login` | `AdminLogin` | 管理员登录入口 |
| `/admin` | `AdminPanel` | 管理面板（需登录） |
| `*` | → 重定向 `/` | 404 兜底 |

---

## 四、页面详解

### 4.1 UserChat — 访客聊天页（`/`）

**用户身份机制：** 匿名用户由浏览器生成唯一 ID 保存在 localStorage，格式 `u_时间戳_随机6位`，无登录步骤即可使用。

**功能列表：**
- WebSocket 自动连接 `/ws/user/{userId}`
- 消息历史加载（HTTP GET `/api/message/history/{userId}`）
- 文本消息发送（Enter 发送）
- 图片/文件上传（点击 + 按钮选择文件）
- 欢迎语展示（服务端发送 `welcome_message` 类型，不保存到数据库）
- 时间分界线（5 分钟内消息聚合，跨日显示日期分隔）
- 自动回复展示（无在线客服时）
- 服务端消息去重（通过 timestamp 和 fileUrl 双重校验）

**消息气泡样式：**
- 用户消息（蓝色气泡，右侧）：`#4A90D9`
- 客服消息（白色气泡，左侧）：`#FFFFFF`
- 图片预览：点击放大打开新窗口

**WebSocket 事件处理：**
| 消息类型 | 处理逻辑 |
|----------|----------|
| `system.agent_assigned` | 更新分配的客服 ID |
| `system.no_agent` | 显示无客服提示 |
| `user_message` / `agent_message` | 追加到消息列表并滚动到底部 |
| `welcome_message` | 显示欢迎语（标记 `_welcome: true` 不存库） |

### 4.2 AgentPanel — 客服工作台（`/agent`）

**布局：** 左侧用户列表（320px 侧边栏）+ 右侧聊天区

**功能列表：**
- WebSocket 自动连接 `/ws/agent/{agentId}`
- 用户列表（按在线、未读数排序）
  - 在线标识（绿色圆点）
  - 未读徽标（红色角标，最多 99+）
  - 无限滚动加载（每次 30 人）
  - 显示最后一条消息预览
- 选择用户后加载聊天历史并标记已读
- 文本消息发送（Enter 发送）
- 图片/文件上传发送
- 个人设置弹窗（修改昵称 + 密码）
- 退出登录

**实时事件处理：**
| 消息类型 | 处理逻辑 |
|----------|----------|
| `new_user` | 有新用户分配 → 刷新用户列表 |
| `user_message` | 当前选中用户 → 追加消息；始终刷新列表 |
| `agent_message` | 当前选中用户 → 追加消息 |
| `user_offline` | 刷新用户列表 |

### 4.3 AdminPanel — 管理面板（`/admin`）

**顶部 Tab 导航：** 客服管理 | 自动回复 | 聊天记录 | 退出

**客服管理 Tab：**
- 表格显示所有客服（admin 过滤不显示）
- 操作：新增、编辑（昵称/密码）、启用/禁用、删除
- 状态列：正常/禁用、在线/离线

**自动回复设置 Tab：**
- 欢迎语编辑（用户 5 分钟内重复进入时自动发送）
- 离线自动回复（无在线客服时用户发消息自动回复）
- 保存成功后显示 ✓ 提示

**聊天记录 Tab：**
- 左侧用户列表 + 右侧只读聊天记录
- 绿色气泡（#E8F5E9）区分客服消息

### 4.4 登录页面

| 页面 | API | 存储 |
|------|-----|------|
| `AgentLogin` | POST `/api/auth/login` | token, agentId, nickname |
| `AdminLogin` | POST `/api/auth/login` (admin 账号) | token, role |
| `UserLogin` | POST `/api/user/login` 或 `/api/user/register` | token, userId, username |

---

## 五、网络层封装（`api.js`）

### request() — HTTP 请求

```js
request('/api/message/history/' + userId)
request('/api/agent/list', { method: 'POST', body: JSON.stringify(data) })
```

- 自动附加 `Authorization: Bearer {token}`（从 localStorage 读取）
- 非 FormData 请求自动设置 `Content-Type: application/json`
- 返回解析后的 JSON

### connectWebSocket() — WebSocket 封装

```js
const ws = connectWebSocket(
  '/ws/user/' + userId,
  onMessage,  // 消息回调
  onOpen,     // 连接成功回调
  onClose     // 断开回调
)
// 返回 { close(), send(data) }
```

- 自动处理 ws/wss 协议选择
- **自动重连**：断开后 3 秒自动重连
- JSON 自动解析（解析失败时以 `raw` 类型返回）

---

## 六、Vite 代理配置

```js
// vite.config.js
server: {
  host: '0.0.0.0',
  port: 3000,
  proxy: {
    '/api':   { target: 'http://localhost:8080', changeOrigin: true },
    '/uploads': { target: 'http://localhost:8080', changeOrigin: true },
    '/ws':    { target: 'ws://localhost:9090', ws: true },
  },
}
```

- `/api/*` → Spring Boot 后端（8080）
- `/uploads/*` → 静态资源目录（8080）
- `/ws/*` → Netty WebSocket（9090）

---

## 七、状态管理

无 Redux 或 Context API 等全局状态库。所有状态管理通过 **React 组件内 useState + useEffect** 结合 localStorage 持久化实现：

| 存储 Key | 用途 |
|----------|------|
| `cs_user_id` | 用户唯一标识（UserChat） |
| `token` | JWT 令牌 |
| `agentId` | 客服 ID |
| `nickname` | 客服昵称 |
| `role` | 角色（admin） |
| `userId` | 注册用户 ID |
| `cs_username` | 用户名 |
| `cs_nickname` | 用户昵称 |

---

## 八、UI 说明

- **CSS 框架**：无，全部使用内联 `style` 对象
- **图标**：SVG 内联图标
- **字体**：系统原生字体栈（`-apple-system, PingFang SC, Microsoft YaHei, sans-serif`）
- **安全区域**：`env(safe-area-inset-top/bottom)` 适配刘海屏
- **颜色主题**：
  - 主色：`#4A90D9`（蓝色，用户气泡/按钮/链接）
  - 背景：`#F5F5F5`
  - 卡片：`#FFFFFF`
  - 成功：`#4CAF50`
  - 错误：`#f44336`

---

## 九、启动方式

```bash
cd frontend
npm install
npm run dev
# 开发服务器默认运行在 http://localhost:3000
```
