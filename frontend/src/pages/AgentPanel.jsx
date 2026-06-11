import { useState, useEffect, useRef, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { connectWebSocket, request } from '../utils/api.js'

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
}

export default function AgentPanel() {
  const navigate = useNavigate()
  const token = localStorage.getItem('token')
  const agentId = localStorage.getItem('agentId')
  const nickname = localStorage.getItem('nickname') || '客服'
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

  // 当前显示的昵称（state，使修改后实时更新），优先从 API 获取
  const [displayNickname, setDisplayNickname] = useState('客服')

  const wsRef = useRef(null)
  const messagesEndRef = useRef(null)
  const userListRef = useRef(null)
  const selectedUserRef = useRef(null)
  const pingTimerRef = useRef(null)

  useEffect(() => {
    if (!token) { navigate('/agent/login'); return }
    refreshProfile()
    connectAgentWs()
    loadUsers(0)
    return () => {
      clearInterval(pingTimerRef.current)
      wsRef.current?.close()
    }
  }, [])

  /** 从服务端同步最新客服资料（nickname 可能被管理员修改过） */
  async function refreshProfile() {
    try {
      const data = await request('/api/agent/profile')
      if (data.nickname) {
        localStorage.setItem('nickname', data.nickname)
        setDisplayNickname(data.nickname)
      }
      if (data.username) {
        localStorage.setItem('username', data.username)
      }
    } catch {}
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
        }
      },
      () => {
        setConnected(true)
        // 开始心跳：每 60 秒发一次 ping，刷新 Redis 在线 TTL
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
    setTimeout(() => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 50)
  }, [])

  useEffect(() => { scrollToBottom() }, [messages, scrollToBottom])

  async function loadUsers(p) {
    setLoading(true)
    try {
      const data = await request('/api/message/users?page=' + p + '&size=30&agentId=' + agentId)
      if (p === 0) {
        setUsers(data)
      } else {
        setUsers(prev => [...prev, ...data])
      }
      setHasMore(data.length === 30)
    } catch {}
    setLoading(false)
  }

  async function selectUser(userId) {
    setSelectedUser(userId)
    selectedUserRef.current = userId
    try {
      const data = await request('/api/message/history/' + userId + '?agentId=' + agentId)
      setMessages(data)
      scrollToBottom()
      // Mark as read — 去掉 agentId 参数，标记该用户的所有消息为已读
      await request('/api/message/mark-read/' + userId, { method: 'POST' })
      loadUsers(0)
    } catch {}
  }

  function sendMessage() {
    if (!input.trim() || !selectedUser) return
    const msg = { type: 'agent_message', userId: selectedUser, content: input.trim(), msgType: 'text' }
    wsRef.current?.send(JSON.stringify(msg))
    setInput('')
  }

  async function uploadFile(e) {
    const file = e.target.files?.[0]
    if (!file) return
    const fd = new FormData()
    fd.append('file', file)
    try {
      const data = await request('/api/message/upload', { method: 'POST', body: fd })
      if (data.url) {
        const isImage = file.type.startsWith('image/')
        wsRef.current?.send(JSON.stringify({ type: 'agent_message', userId: selectedUser, content: file.name, msgType: isImage ? 'image' : 'file', fileUrl: data.url }))
      }
    } catch {}
    e.target.value = ''
  }

  function handleScroll() {
    const el = userListRef.current
    if (el && el.scrollTop + el.clientHeight >= el.scrollHeight - 50 && hasMore && !loading) {
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
    } catch {}
  }

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('agentId')
    localStorage.removeItem('nickname')
    localStorage.removeItem('username')
    navigate('/agent/login')
  }

  // Sort users
  const sortedUsers = [...users].sort((a, b) => {
    if (a.online !== b.online) return a.online ? -1 : 1
    return (b.unread || 0) - (a.unread || 0)
  })

  return (
    <div style={{ display: 'flex', height: '100vh', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      {/* Sidebar */}
      <div style={{ width: 320, borderRight: '1px solid #E0E0E0', display: 'flex', flexDirection: 'column', background: '#F8F9FA' }}>
        <div style={{ padding: '16px', borderBottom: '1px solid #E0E0E0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <span style={{ fontSize: 16, fontWeight: 600 }}>{displayNickname}</span>
            <span style={{ ...statusDot, background: connected ? '#4CAF50' : '#999', marginLeft: 8 }} />
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button style={smallBtn} onClick={() => { setShowProfile(true); setNewNickname(displayNickname === '客服' ? '' : displayNickname) }}>设置</button>
            <button style={smallBtn} onClick={handleLogout}>退出</button>
          </div>
        </div>
        <div style={{ padding: '8px 12px', fontSize: 13, color: '#666', borderBottom: '1px solid #E0E0E0' }}>
          用户列表 ({users.length})
        </div>
        <div ref={userListRef} onScroll={handleScroll} style={{ flex: 1, overflowY: 'auto' }}>
          {sortedUsers.map(u => (
            <div key={u.userId}
              onClick={() => selectUser(u.userId)}
              style={{
                padding: '12px 16px', cursor: 'pointer', borderBottom: '1px solid #F0F0F0',
                background: selectedUser === u.userId ? '#E3F2FD' : 'transparent',
                display: 'flex', flexDirection: 'column', gap: 4
              }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 14, fontWeight: u.unread > 0 ? 600 : 400, position: 'relative', display: 'inline-block' }}>
                  {u.nickname || u.userId}
                  {u.online && <span style={{ color: '#4CAF50', marginLeft: 6, fontSize: 11 }}>●</span>}
                  {u.unread > 0 && <span style={{
                    position: 'absolute', top: -8, right: -14,
                    background: '#f44336', color: '#fff', borderRadius: 10,
                    minWidth: 18, height: 18, fontSize: 11, fontWeight: 600,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    padding: '0 4px', boxSizing: 'border-box',
                  }}>{u.unread > 99 ? '99+' : u.unread}</span>}
                </span>
              </div>
              <div style={{ fontSize: 12, color: '#999', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {u.lastMessage || '暂无消息'}
              </div>
            </div>
          ))}
          {loading && <div style={{ textAlign: 'center', padding: 12, color: '#999' }}>加载中...</div>}
        </div>
      </div>

      {/* Chat area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: '#F5F5F5' }}>
        {!selectedUser ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999', fontSize: 16 }}>
            请选择一个用户开始聊天
          </div>
        ) : (
          <>
            <div style={{ padding: '12px 20px', background: '#fff', borderBottom: '1px solid #E0E0E0', fontSize: 15, fontWeight: 600 }}>
              {users.find(u => u.userId === selectedUser)?.nickname || selectedUser}
            </div>
            <div style={{ flex: 1, overflowY: 'auto', padding: 20 }}>
              {messages.map((msg, i) => (
                <div key={i} style={{ display: 'flex', justifyContent: msg.direction === 'user' ? 'flex-start' : 'flex-end', marginBottom: 12 }}>
                  <div style={{
                    maxWidth: '60%', padding: '10px 16px', borderRadius: 8,
                    background: msg.direction === 'user' ? '#fff' : '#4A90D9',
                    color: msg.direction === 'user' ? '#333' : '#fff',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
                    fontSize: 14, lineHeight: 1.5, wordBreak: 'break-word'
                  }}>
                    {msg.msgType === 'image' ? (
                      <img src={'' + msg.fileUrl} alt="" style={{ maxWidth: 200, borderRadius: 4 }} onClick={() => window.open('' + msg.fileUrl)} />
                    ) : msg.msgType === 'file' ? (
                      <div><span>📎</span><a href={'' + msg.fileUrl} target="_blank" rel="noreferrer" style={{ color: msg.direction === 'user' ? '#4A90D9' : '#fff', marginLeft: 4 }}>{msg.content}</a></div>
                    ) : (
                      <span dangerouslySetInnerHTML={{ __html: msg.content }} />
                    )}
                    <div style={{ fontSize: 11, opacity: 0.7, marginTop: 4, textAlign: 'right' }}>
                      {formatTime(msg.timestamp || msg.createdAt)}
                    </div>
                  </div>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>
            <div style={{ padding: '12px 20px', background: '#fff', borderTop: '1px solid #E0E0E0', display: 'flex', gap: 10, alignItems: 'flex-end' }}>
              <input type="file" id="agentFileInput" style={{display:'none'}} onChange={uploadFile} />
              <button style={{ padding: '8px 12px', border: '1px solid #ddd', borderRadius: 4, background: '#fff', cursor: 'pointer', fontSize: 13 }} onClick={() => document.getElementById('agentFileInput').click()}>📎</button>
              <textarea
                value={input} onChange={e => setInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }}
                style={{ flex: 1, border: '1px solid #ddd', borderRadius: 6, padding: '10px 12px', fontSize: 14, outline: 'none', resize: 'none', minHeight: 20, maxHeight: 80 }}
                placeholder="输入消息..."
                rows={1}
              />
              <button onClick={sendMessage} style={{ padding: '10px 24px', background: '#4A90D9', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 14 }}>发送</button>
            </div>
          </>
        )}
      </div>

      {/* Profile modal */}
      {showProfile && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ marginBottom: 16 }}>个人设置</h3>
            <div style={{ marginBottom: 12 }}>
              <label style={{ fontSize: 13, color: '#666', display: 'block', marginBottom: 4 }}>用户名（不可修改）</label>
              <input style={{...modalInput, background: '#f5f5f5', color: '#999'}} value={username} disabled />
            </div>
            <div style={{ marginBottom: 12 }}>
              <label style={{ fontSize: 13, color: '#666', display: 'block', marginBottom: 4 }}>修改昵称</label>
              <input style={modalInput} value={newNickname} onChange={e => setNewNickname(e.target.value)} placeholder="新昵称" />
            </div>
            <div style={{ marginBottom: 20 }}>
              <label style={{ fontSize: 13, color: '#666', display: 'block', marginBottom: 4 }}>修改密码</label>
              <input style={modalInput} type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="新密码" />
            </div>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowProfile(false)} style={{ padding: '8px 20px', border: '1px solid #ddd', borderRadius: 4, background: '#fff', cursor: 'pointer' }}>取消</button>
              <button onClick={updateProfile} style={{ padding: '8px 20px', background: '#4A90D9', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer' }}>保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const statusDot = { width: 8, height: 8, borderRadius: '50%', display: 'inline-block' }
const smallBtn = { padding: '4px 12px', border: '1px solid #ddd', borderRadius: 4, background: '#fff', cursor: 'pointer', fontSize: 12 }
const modalOverlay = { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }
const modalBox = { background: '#fff', borderRadius: 12, padding: 24, width: 360, boxShadow: '0 4px 20px rgba(0,0,0,0.15)' }
const modalInput = { width: '100%', padding: '10px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 14, outline: 'none', boxSizing: 'border-box' }
