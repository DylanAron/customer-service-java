import { useState, useEffect, useRef, useCallback } from 'react'
import { connectWebSocket, request } from '../utils/api.js'

function getUserId() {
  let uid = localStorage.getItem('cs_user_id')
  if (!uid) {
    uid = 'u_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
    localStorage.setItem('cs_user_id', uid)
  }
  return uid
}

function getChannelCode() {
  // 简单检测：移动端触摸屏且窄屏 → h5，否则 → pc
  const isMobile = /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent)
  if (isMobile) return 'h5'
  // 通过屏幕宽度辅助判断触摸设备
  if (window.innerWidth < 768 && 'ontouchstart' in window) return 'h5'
  return 'pc'
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
}

function formatTimeFull(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return (d.getMonth() + 1).toString().padStart(2, '0') + '-' + d.getDate().toString().padStart(2, '0') + ' ' + d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
}

function isOver5Min(t1, t2) {
  if (!t1 || !t2) return true
  return Math.abs(new Date(t1).getTime() - new Date(t2).getTime()) >= 5 * 60 * 1000
}

function formatDate(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const month = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  return month + '-' + day
}

function isSameDay(ts1, ts2) {
  if (!ts1 || !ts2) return false
  const d1 = new Date(ts1), d2 = new Date(ts2)
  return d1.getFullYear() === d2.getFullYear() && d1.getMonth() === d2.getMonth() && d1.getDate() === d2.getDate()
}

export default function UserChat() {
  const userId = getUserId()
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [connected, setConnected] = useState(false)
  const [agentAssigned, setAgentAssigned] = useState(null)
  const [noAgent, setNoAgent] = useState(false)
  const messagesEndRef = useRef(null)
  const wsRef = useRef(null)
  const fileInputRef = useRef(null)
  const inputRef = useRef(null)
  const pingTimerRef = useRef(null)

  const scrollToBottom = useCallback(() => {
    setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 50)
  }, [])

  useEffect(() => {
    request('/api/message/history/' + userId).then(data => {
      if (data && data.length > 0) setMessages(data)
    }).catch(() => {})

    const ws = connectWebSocket('/ws/user/' + userId,
      (msg) => {
        if (msg.type === 'system' && msg.agent_assigned) {
          setAgentAssigned(msg.agent_assigned)
          setNoAgent(false)
        } else if (msg.type === 'system' && msg.no_agent) {
          setNoAgent(true)
        } else if (msg.type === 'user_message' || msg.type === 'agent_message') {
          setMessages(prev => {
            if (prev.find(m => m.timestamp === msg.timestamp)) return prev
            if (msg.fileUrl && prev.find(m => m.fileUrl === msg.fileUrl)) return prev
            return [...prev, msg]
          })
          scrollToBottom()
        } else if (msg.type === 'welcome_message') {
          // 欢迎语只追加到前端显示，不保存到数据库
          // 过滤纯空白内容（如 contentEditable 保存的 <p></p> 或 空字符串）
          console.log('welcome_message received:', msg.content)
          const text = (msg.content || '').replace(/<[^>]*>/g, '').trim()
          if (!text) {
            console.log('welcome_message filtered out (empty after strip)')
            return
          }
          const welcomeMsg = { type: 'agent_message', content: msg.content, direction: 'agent', msgType: 'text', _welcome: true }
          setMessages(prev => [...prev, welcomeMsg])
          scrollToBottom()
        }
      },
      () => {
        setConnected(true)
        // 用户端心跳：每 60 秒刷新 Redis 在线 TTL
        clearInterval(pingTimerRef.current)
        pingTimerRef.current = setInterval(() => {
          ws?.send(JSON.stringify({ type: 'ping' }))
        }, 60000)
      },
      () => {
        setConnected(false)
        clearInterval(pingTimerRef.current)
      }
    )
    wsRef.current = ws
    return () => {
      clearInterval(pingTimerRef.current)
      ws.close()
    }
  }, [userId, scrollToBottom])

  useEffect(() => { scrollToBottom() }, [messages, scrollToBottom])

  function sendMessage() {
    if (!input.trim()) return
    const msg = { type: 'user_message', content: input.trim(), msgType: 'text', channelCode: getChannelCode() }
    wsRef.current?.send(JSON.stringify(msg))
    setInput('')
    inputRef.current?.focus()
  }
  function handleFileUpload(e) {
    const file = e.target.files?.[0]
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    request('/api/message/upload', { method: 'POST', body: formData }).then(data => {
      if (data.url) {
        const isImage = file.type.startsWith('image/')
        wsRef.current?.send(JSON.stringify({ type: 'user_message', content: file.name, msgType: isImage ? 'image' : 'file', fileUrl: data.url, channelCode: getChannelCode() }))
      }
    }).catch(() => {})
    e.target.value = ''
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  function handleInputChange(e) {
    setInput(e.target.value)
    e.target.style.height = 'auto'
    e.target.style.height = Math.min(e.target.scrollHeight, 100) + 'px'
  }

  return (
    <div style={styles.container}>
      {/* Notch / Status Bar */}
      <div style={styles.notchBar} />

      {/* Header */}
      <div style={styles.header}>
        <div style={styles.headerInner}>
          <div style={styles.headerLeft}>
            <button style={styles.backBtn} onClick={() => {}}>
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#333" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="15 18 9 12 15 6" />
              </svg>
            </button>
          </div>
          <div style={styles.headerCenter}>
            <span style={styles.headerTitle}>在线客服</span>
            {!connected && <span style={styles.headerSub}>(连接中...)</span>}
          </div>
          <div style={styles.headerRight} />
        </div>
      </div>

      {/* Messages */}
      <div style={styles.messagesArea}>
        <div style={styles.messagesInner}>
        {messages.length === 0 && (
          <div style={styles.welcomeMsg}>
            <div style={styles.welcomeIcon}>
              <svg viewBox="0 0 72 72" width="72" height="72" fill="none">
                <rect x="8" y="12" width="56" height="40" rx="6" fill="#E8F0FE" stroke="#4A90D9" strokeWidth="1.5"/>
                <path d="M28 44l-8 8v-8h-4c-3.3 0-6-2.7-6-6V18c0-3.3 2.7-6 6-6h40c3.3 0 6 2.7 6 6v20c0 3.3-2.7 6-6 6H36l-8 8z" fill="#4A90D9" opacity="0.08"/>
                <path d="M32 28h8m-8 6h12" stroke="#4A90D9" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
            <p style={styles.welcomeText}>您好！欢迎来到在线客服</p>
            <p style={styles.welcomeSubtext}>
              {noAgent
                ? '当前没有在线客服，您可留言，我们会尽快回复您'
                : '请描述您的问题，我们会尽快为您解答'}
            </p>
          </div>
        )}
        {messages.map((msg, i) => {
          const isUser = msg.direction === 'user'
          const prev = i > 0 ? messages[i - 1] : null
          const showDate = !prev || !isSameDay(prev.timestamp || prev.createdAt, msg.timestamp || msg.createdAt)
          const showTime = showDate || !prev || isOver5Min(prev.timestamp || prev.createdAt, msg.timestamp || msg.createdAt)
          const time = msg.timestamp || msg.createdAt
          const showAvatar = !isUser
          return (
            <div key={i}>
              {showDate && <div style={styles.dateDivider}>{formatDate(time)}</div>}
              {showTime && !showDate && <div style={styles.dateDivider}>{formatTime(time)}</div>}
              {/* Agent message */}
              {!isUser && (
                <div style={styles.agentRow}>
                  <div style={styles.agentAvatarCol}>
                    <div style={styles.agentAvatar}>客</div>
                  </div>
                  <div style={styles.agentContent}>
                    {/*<div style={styles.agentName}>客服</div>*/}
                    <div style={styles.agentBubbleRow}>
                      <div style={styles.agentBubble}>
                        <div style={styles.agentTail} />
                        {msg.msgType === 'image' ? (
                          msg.fileUrl ? (
                            <img src={'' + msg.fileUrl} alt="" style={styles.msgImage} onClick={() => window.open('' + msg.fileUrl)} />
                          ) : <span>{msg.content}</span>
                        ) : msg.msgType === 'file' ? (
                          msg.fileUrl ? (
                            <div style={styles.fileBox}>
                              <span>📎</span>
                              <a href={'' + msg.fileUrl} target="_blank" rel="noreferrer" style={{color: '#4A90D9', marginLeft: 6, textDecoration: 'none'}}>{msg.content}</a>
                            </div>
                          ) : <span>{msg.content}</span>
                        ) : (
                          <span style={styles.msgText} dangerouslySetInnerHTML={{ __html: msg.content }} />
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              )}
              {/* User message - 同客服样式，蓝色 */}
              {isUser && (
                <div style={styles.userRow}>
                  <div style={styles.userBubble}>
                    <div style={styles.userBubbleTail} />
                    {msg.msgType === 'image' ? (
                      msg.fileUrl ? (
                        <img src={'' + msg.fileUrl} alt="" style={styles.msgImage} onClick={() => window.open('' + msg.fileUrl)} />
                      ) : <span>{msg.content}</span>
                    ) : msg.msgType === 'file' ? (
                      msg.fileUrl ? (
                        <div style={styles.fileBox}>
                          <span>📎</span>
                          <a href={'' + msg.fileUrl} target="_blank" rel="noreferrer" style={{color: '#fff', marginLeft: 6, textDecoration: 'none'}}>{msg.content}</a>
                        </div>
                      ) : <span>{msg.content}</span>
                    ) : (
                      <span style={styles.msgTextUser}>{msg.content}</span>
                    )}
                  </div>
                  <div style={styles.userAvatar}>我</div>
                </div>
              )}
            </div>
          )
        })}
        <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input */}
      <div style={styles.inputWrapper}>
        <div style={styles.inputContainer}>
          <button style={styles.plusBtn} onClick={() => fileInputRef.current?.click()}>
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#666" strokeWidth="2" strokeLinecap="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
          <input ref={fileInputRef} type="file" style={{display: 'none'}} onChange={handleFileUpload} />
          <textarea
            ref={inputRef}
            style={styles.inputBox}
            value={input}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            placeholder="请输入您的问题..."
            rows={1}
          />
          {input.trim() ? (
            <button style={styles.sendBtn} onClick={sendMessage}>
              <svg viewBox="0 0 24 24" width="22" height="22" fill="#fff">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
              </svg>
            </button>
          ) : (
            <div style={styles.sendBtnPlaceholder} />
          )}
        </div>
      </div>
    </div>
  )
}

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    height: '100vh',
    height: '100dvh',
    background: '#F5F5F5',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif',
    maxWidth: '100%',
    margin: '0 auto',
  },

  // Notch / Status Bar
  notchBar: {
    height: 'env(safe-area-inset-top, 0px)',
    background: '#FFFFFF',
    flexShrink: 0,
  },

  // Header
  header: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'stretch',
    background: '#FFFFFF',
    borderBottom: '1px solid #E0E0E0',
    borderBottomLeftRadius: 16,
    borderBottomRightRadius: 16,
    position: 'sticky',
    top: 0,
    zIndex: 10,
    paddingTop: 'env(safe-area-inset-top, 0px)',
    overflow: 'hidden',
    boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
  },
  backBtn: {
    background: 'none',
    border: 'none',
    padding: '4px',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 32,
    height: 32,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 18,
    fontWeight: 600,
    color: '#fff',
    letterSpacing: 0.5,
    marginRight: 32,
  },
  headerRight: { width: 48 },

  // Header inner
  headerInner: {
    display: 'flex',
    alignItems: 'center',
    width: '100%',
    padding: '8px 0',
    minHeight: 44,
  },
  headerLeft: {
    width: 48,
    display: 'flex',
    alignItems: 'center',
  },
  backBtn: {
    background: 'none',
    border: 'none',
    padding: '4px',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerCenter: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: 600,
    color: '#222',
  },
  headerSub: {
    fontSize: 11,
    color: '#999',
    marginTop: 1,
  },

  // Messages
  messagesArea: {
    flex: 1,
    overflowY: 'auto',
    padding: 0,
    display: 'flex',
    flexDirection: 'column',
    background: '#F5F5F5',
  },
  messagesInner: {
    padding: '20px 16px',
    display: 'flex',
    flexDirection: 'column',
    flex: 1,
  },

  welcomeMsg: {
    textAlign: 'center',
    padding: '40px 20px',
    color: '#999',
  },
  welcomeIcon: { marginBottom: 20 },
  welcomeText: { fontSize: 16, color: '#555', marginBottom: 8, fontWeight: 500 },
  welcomeSubtext: { fontSize: 13, color: '#999', lineHeight: 1.6, margin: 0 },

  timeDivider: {
    textAlign: 'center',
    color: '#B8B8B8',
    fontSize: 12,
    marginBottom: 16,
    marginTop: 8,
  },
  dateDivider: {
    textAlign: 'center',
    color: '#B8B8B8',
    fontSize: 12,
    marginBottom: 16,
    marginTop: 4,
  },

  // Agent message (left)
  agentRow: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: 10,
    marginBottom: 18,
  },
  agentAvatarCol: {
    width: 38,
    flexShrink: 0,
    display: 'flex',
    justifyContent: 'center',
  },
  agentAvatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    background: '#4A90D9',
    color: '#fff',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 14,
    fontWeight: 600,
  },
  agentContent: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    maxWidth: 'calc(100% - 56px)',
  },
  agentName: { fontSize: 12, color: '#888', marginBottom: 2 },
  agentBubbleRow: {
    marginTop:18,
    display: 'flex',
    alignItems: 'flex-end',
    gap: 6,
    position: 'relative',
  },
  agentTail: {
    position: 'absolute',
    left: -8,
    top: 0,
    width: 0,
    height: 0,
    borderTop: '8px solid #fff',
    borderLeft: '8px solid transparent',
  },
  agentBubble: {
    background: '#fff',
    padding: '10px 14px',
    borderRadius: 12,
    borderTopLeftRadius: 0,
    boxShadow: '0 1px 3px rgba(0,0,0,0.07)',
    wordBreak: 'break-word',
    whiteSpace: 'pre-wrap',
    fontSize: 15,
    lineHeight: 1.6,
    color: '#222',
  },
  msgText: {
    wordBreak: 'break-word',
    whiteSpace: 'pre-wrap',
    fontSize: 15,
    lineHeight: 1.6,
  },

  // User message (right)
  userRow: {
    display: 'flex',
    justifyContent: 'flex-end',
    alignItems: 'self-start',
    gap: 0,
    marginBottom: 18,
  },
  userAvatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    background: '#4A90D9',
    color: '#fff',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 14,
    fontWeight: 600,
    flexShrink: 0,
    marginLeft: 6,
  },
  userBubbleTail: {
    position: 'absolute',
    right: -8,
    top: 0,
    width: 0,
    height: 0,
    borderTop: '8px solid #4A90D9',
    borderRight: '8px solid transparent',
  },
  userTail: { display: 'none' },
  userBubble: {
    marginTop:18,
    background: '#4A90D9',
    padding: '10px 14px',
    borderRadius: 12,
    borderTopRightRadius: 0,
    color: '#fff',
    boxShadow: '0 1px 2px rgba(0,0,0,0.08)',
    wordBreak: 'break-word',
    whiteSpace: 'pre-wrap',
    fontSize: 15,
    lineHeight: 1.6,
    maxWidth: '60%',
    position: 'relative',
  },
  msgTextUser: {
    wordBreak: 'break-word',
    whiteSpace: 'pre-wrap',
    fontSize: 15,
    lineHeight: 1.6,
  },

  msgImage: {
    maxWidth: 200,
    maxHeight: 200,
    borderRadius: 6,
    cursor: 'pointer',
    display: 'block',
  },
  fileBox: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    fontSize: 14,
  },
  msgTime: {
    fontSize: 11,
    color: '#B8B8B8',
    whiteSpace: 'nowrap',
    marginBottom: 4,
    display: 'inline-block',
  },
  msgTimeUser: {
    fontSize: 11,
    color: 'rgba(255,255,255,0.6)',
    whiteSpace: 'nowrap',
    marginBottom: 4,
    display: 'block',
  },

  // Input
  inputWrapper: {
    background: '#fff',
    borderTop: '1px solid #E0E0E0',
    padding: '8px 12px',
    paddingBottom: 'calc(8px + env(safe-area-inset-bottom, 0px))',
  },
  inputContainer: {
    display: 'flex',
    alignItems: 'flex-end',
    gap: 8,
    background: '#F5F5F7',
    borderRadius: 22,
    padding: '4px 4px 4px 10px',
    border: '1px solid #E0E0E4',
  },
  plusBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    border: 'none',
    background: 'transparent',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  inputBox: {
    flex: 1,
    border: 'none',
    padding: '8px 0',
    fontSize: 15,
    outline: 'none',
    resize: 'none',
    maxHeight: 100,
    fontFamily: 'inherit',
    background: 'transparent',
    lineHeight: 1.4,
    color: '#333',
  },
  sendBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    border: 'none',
    background: '#4A90D9',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  sendBtnPlaceholder: {
    width: 34,
    height: 34,
    flexShrink: 0,
  },
}
