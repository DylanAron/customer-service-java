import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { connectWebSocket, request } from '../utils/api.js'
import customerServiceAvatar from '../assets/customer_service_avatar.webp'
import userAvatar01 from '../assets/user_avatars/user_avatar_01.webp'
import userAvatar02 from '../assets/user_avatars/user_avatar_02.webp'
import userAvatar03 from '../assets/user_avatars/user_avatar_03.webp'
import userAvatar04 from '../assets/user_avatars/user_avatar_04.webp'
import userAvatar05 from '../assets/user_avatars/user_avatar_05.webp'
import userAvatar06 from '../assets/user_avatars/user_avatar_06.webp'
import userAvatar07 from '../assets/user_avatars/user_avatar_07.webp'
import userAvatar08 from '../assets/user_avatars/user_avatar_08.webp'
import userAvatar09 from '../assets/user_avatars/user_avatar_09.webp'
import userAvatar10 from '../assets/user_avatars/user_avatar_10.webp'
import './workspace.css'

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
}

const userAvatars = [
  userAvatar01,
  userAvatar02,
  userAvatar03,
  userAvatar04,
  userAvatar05,
  userAvatar06,
  userAvatar07,
  userAvatar08,
  userAvatar09,
  userAvatar10,
]

function getUserAvatar(userId) {
  const text = String(userId || '')
  let hash = 0
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0
  }
  return userAvatars[hash % userAvatars.length]
}

export default function AgentPanel() {
  const navigate = useNavigate()
  const token = localStorage.getItem('token')
  const agentId = localStorage.getItem('agentId')
  const username = localStorage.getItem('username') || ''

  const [users, setUsers] = useState([])
  const [selectedUser, setSelectedUser] = useState(null)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [connected, setConnected] = useState(false)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [showProfile, setShowProfile] = useState(false)
  const [newNickname, setNewNickname] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [displayNickname, setDisplayNickname] = useState(localStorage.getItem('nickname') || '客服')
  const [toast, setToast] = useState(null)

  const wsRef = useRef(null)
  const messagesRef = useRef(null)
  const messagesEndRef = useRef(null)
  const userListRef = useRef(null)
  const selectedUserRef = useRef(null)
  const selectRequestRef = useRef(0)
  const pingTimerRef = useRef(null)

  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), 3000)
    return () => clearTimeout(timer)
  }, [toast])

  useEffect(() => {
    if (!token || !agentId || localStorage.getItem('role') !== 'agent') {
      navigate('/agent/login')
      return
    }
    refreshProfile()
    connectAgentWs()
    loadUsers(0)
    return () => {
      clearInterval(pingTimerRef.current)
      wsRef.current?.close()
    }
  }, [])

  async function refreshProfile() {
    try {
      const data = await request('/api/agent/profile')
      if (data.nickname) {
        localStorage.setItem('nickname', data.nickname)
        setDisplayNickname(data.nickname)
      }
      if (data.username) localStorage.setItem('username', data.username)
    } catch (err) {
      console.error('加载客服资料失败', err)
    }
  }

  function connectAgentWs() {
    const ws = connectWebSocket('/ws/agent/' + agentId,
      (msg) => {
        if (msg.type === 'new_user') {
          loadUsers(0)
        } else if (msg.type === 'agent_message') {
          if (msg.userId === selectedUserRef.current) {
            setMessages(prev => {
              if (msg._local || prev.find(m => m.timestamp === msg.timestamp)) return prev
              return [...prev, msg]
            })
            scrollToBottom()
          }
        } else if (msg.type === 'user_message') {
          if (msg.userId === selectedUserRef.current) {
            setMessages(prev => {
              if (prev.find(m => m.timestamp === msg.timestamp)) return prev
              return [...prev, msg]
            })
            scrollToBottom()
          }
          loadUsers(0)
        } else if (msg.type === 'user_offline') {
          loadUsers(0)
        } else if (msg.type === 'agent_error') {
          setToast(msg.message || '消息发送失败')
        }
      },
      () => {
        setConnected(true)
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
  }

  const scrollToBottom = useCallback(() => {
    setTimeout(() => {
      const el = messagesRef.current
      if (el) {
        el.scrollTop = el.scrollHeight
        return
      }
      messagesEndRef.current?.scrollIntoView({ block: 'end' })
    }, 50)
  }, [])

  useEffect(() => { scrollToBottom() }, [messages, scrollToBottom])

  async function loadUsers(p) {
    setLoading(true)
    try {
      const data = await request('/api/message/users?page=' + p + '&size=30&agentId=' + agentId)
      if (p === 0) setUsers(data)
      else setUsers(prev => [...prev, ...data])
      setHasMore(data.length === 30)
    } catch (err) {
      console.error('加载用户列表失败', err)
    } finally {
      setLoading(false)
    }
  }

  async function selectUser(userId) {
    const requestId = selectRequestRef.current + 1
    selectRequestRef.current = requestId
    setSelectedUser(userId)
    selectedUserRef.current = userId
    setMessages([])
    try {
      const data = await request('/api/message/history/' + userId + '?agentId=' + agentId)
      if (selectRequestRef.current !== requestId) return
      setMessages(data)
      scrollToBottom()
      await request('/api/message/mark-read/' + userId + '?agentId=' + agentId, { method: 'POST' })
      if (selectRequestRef.current === requestId) {
        setUsers(prev => prev.map(u => u.userId === userId ? { ...u, unread: 0 } : u))
      }
    } catch (err) {
      console.error('加载消息历史失败', err)
    }
  }

  function sendMessage() {
    if (!input.trim() || !selectedUser) return
    wsRef.current?.send(JSON.stringify({
      type: 'agent_message',
      userId: selectedUser,
      content: input.trim(),
      msgType: 'text',
    }))
    setInput('')
  }

  async function uploadFile(e) {
    const file = e.target.files?.[0]
    if (!file || !selectedUser) return
    const fd = new FormData()
    fd.append('file', file)
    try {
      const data = await request('/api/message/upload', { method: 'POST', body: fd })
      if (data.url) {
        const isImage = file.type.startsWith('image/')
        wsRef.current?.send(JSON.stringify({
          type: 'agent_message',
          userId: selectedUser,
          content: file.name,
          msgType: isImage ? 'image' : 'file',
          fileUrl: data.url,
        }))
      }
    } catch (err) {
      console.error('上传文件失败', err)
    }
    e.target.value = ''
  }

  function handleScroll() {
    const el = userListRef.current
    if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 160 && hasMore && !loading) {
      const newPage = page + 1
      setPage(newPage)
      loadUsers(newPage)
    }
  }

  async function updateProfile() {
    try {
      if (newNickname) {
        await request('/api/agent/nickname', { method: 'PUT', body: JSON.stringify({ nickname: newNickname }) })
        localStorage.setItem('nickname', newNickname)
        setDisplayNickname(newNickname)
      }
      if (newPassword) {
        await request('/api/agent/password', { method: 'PUT', body: JSON.stringify({ password: newPassword }) })
      }
      setShowProfile(false)
      setNewNickname('')
      setNewPassword('')
    } catch (err) {
      console.error('更新个人资料失败', err)
    }
  }

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('agentId')
    localStorage.removeItem('nickname')
    localStorage.removeItem('username')
    navigate('/agent/login')
  }

  const visibleUsers = users
  const selectedUserInfo = users.find(u => u.userId === selectedUser)
  const selectedUserAvatar = getUserAvatar(selectedUser)
  const totalUnread = users.reduce((sum, u) => sum + (u.unread || 0), 0)
  const onlineUsers = users.filter(u => u.online).length

  return (
    <div className="cs-workspace">
      <aside className="cs-inbox">
        <div className="cs-inbox-head">
          <div className="cs-agent-row">
            <img className="cs-agent-avatar" src={customerServiceAvatar} alt="" />
            <div>
              <div className="cs-agent-name">{displayNickname}</div>
              <div className="cs-status-line">
                <span className="cs-dot" style={{ background: connected ? '#16a34a' : '#94a3b8' }} />
                {connected ? '在线接待中' : '连接中'}
              </div>
            </div>
            <div className="cs-inbox-actions">
              <button className="cs-small-btn" onClick={() => { setShowProfile(true); setNewNickname(displayNickname === '客服' ? '' : displayNickname) }}>设置</button>
              <button className="cs-small-btn" onClick={handleLogout}>退出</button>
            </div>
          </div>

          <div className="cs-inbox-stats">
            <div className="cs-stat"><strong>{users.length}</strong><span>会话</span></div>
            <div className="cs-stat"><strong>{totalUnread}</strong><span>未读</span></div>
            <div className="cs-stat"><strong>{onlineUsers}</strong><span>在线</span></div>
          </div>
        </div>

        <div className="cs-list-bar">
          <span>用户列表</span>
          <button className="cs-link-btn" onClick={() => loadUsers(0)}>刷新</button>
        </div>

        <div ref={userListRef} onScroll={handleScroll} className="cs-user-list">
          {visibleUsers.map(u => {
            const active = selectedUser === u.userId
            const name = u.nickname || u.userId
            const avatar = getUserAvatar(u.userId)
            return (
              <button key={u.userId} onClick={() => selectUser(u.userId)} className={'cs-user-card' + (active ? ' is-active' : '')}>
                <img className="cs-user-avatar" src={avatar} alt="" />
                <span style={{ minWidth: 0 }}>
                  <span className="cs-user-name-row">
                    <span className="cs-user-name">{name}</span>
                    {u.online && <span className="cs-online-pill">在线</span>}
                  </span>
                  <span className="cs-last-message">{u.lastMessage || '暂无消息'}</span>
                </span>
                {u.unread > 0 && <span className="cs-unread">{u.unread > 99 ? '99+' : u.unread}</span>}
              </button>
            )
          })}
          {loading && <div className="cs-loading">加载中...</div>}
          {!loading && visibleUsers.length === 0 && <div className="cs-empty-list">暂无会话</div>}
        </div>
      </aside>

      <main className="cs-chat">
        {!selectedUser ? (
          <div className="cs-empty">
            <div className="cs-empty-card">
              <div className="cs-empty-icon">聊</div>
              <h2>选择一个用户开始接待</h2>
              <p>新消息和待认领用户会自动出现在左侧列表。</p>
            </div>
          </div>
        ) : (
          <>
            <div className="cs-chat-header">
              <div className="cs-chat-title-row">
                <img className="cs-user-avatar" src={selectedUserAvatar} alt="" />
                <div>
                  <div className="cs-chat-user">{selectedUserInfo?.nickname || selectedUser}</div>
                  <div className="cs-chat-status">
                    <span className="cs-dot" style={{ background: selectedUserInfo?.online ? '#16a34a' : '#cbd5e1' }} />
                    {selectedUserInfo?.online ? '用户在线' : '用户离线'}
                  </div>
                </div>
              </div>
            </div>

            <div ref={messagesRef} className="cs-messages">
              {messages.map((msg, i) => {
                const isUser = msg.direction === 'user'
                return (
                  <div key={i} className={'cs-message-row' + (isUser ? '' : ' is-agent')}>
                    {isUser && <img className="cs-message-avatar" src={selectedUserAvatar} alt="" />}
                    <div className={'cs-bubble ' + (isUser ? 'is-user' : 'is-agent')}>
                      {msg.msgType === 'image' ? (
                        <img src={'' + msg.fileUrl} alt="" className="cs-msg-image" onClick={() => window.open('' + msg.fileUrl)} />
                      ) : msg.msgType === 'file' ? (
                        <a href={'' + msg.fileUrl} target="_blank" rel="noreferrer" className="cs-file-link">{msg.content}</a>
                      ) : (
                        <span dangerouslySetInnerHTML={{ __html: msg.content }} />
                      )}
                      <div className="cs-bubble-time">{formatTime(msg.timestamp || msg.createdAt)}</div>
                    </div>
                    {!isUser && <img className="cs-message-avatar" src={customerServiceAvatar} alt="" />}
                  </div>
                )
              })}
              <div ref={messagesEndRef} />
            </div>

            <div className="cs-composer">
              <input type="file" id="agentFileInput" style={{ display: 'none' }} onChange={uploadFile} />
              <button className="cs-attach" disabled={!selectedUser} onClick={() => document.getElementById('agentFileInput').click()}>附件</button>
              <textarea
                value={input}
                onChange={e => setInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }}
                placeholder="输入消息..."
                rows={1}
              />
              <button className="cs-send" onClick={sendMessage}>发送</button>
            </div>
          </>
        )}
      </main>

      {toast && (
        <div className="cs-toast">{toast}</div>
      )}
      {showProfile && (
        <div className="cs-modal-mask">
          <div className="cs-modal">
            <div className="cs-modal-head">
              <h3>个人设置</h3>
              <button className="cs-close" onClick={() => setShowProfile(false)}>×</button>
            </div>
            <label className="cs-field">
              <span>用户名</span>
              <input className="cs-input" value={username} disabled />
            </label>
            <label className="cs-field">
              <span>修改昵称</span>
              <input className="cs-input" value={newNickname} onChange={e => setNewNickname(e.target.value)} placeholder="新的显示名称" />
            </label>
            <label className="cs-field">
              <span>修改密码</span>
              <input className="cs-input" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="新的登录密码" />
            </label>
            <div className="cs-modal-actions">
              <button className="cs-secondary" onClick={() => setShowProfile(false)}>取消</button>
              <button className="cs-primary" onClick={updateProfile}>保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
