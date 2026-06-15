import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { request } from '../utils/api.js'
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

function Toolbar({ onAction }) {
  return (
    <div className="cs-toolbar">
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('bold') }} title="加粗"><b>B</b></button>
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('italic') }} title="斜体"><i>I</i></button>
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('underline') }} title="下划线"><u>U</u></button>
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('strikeThrough') }} title="删除线"><s>S</s></button>
      <span className="cs-toolbar-divider" />
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('insertUnorderedList') }} title="无序列表">•</button>
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('insertOrderedList') }} title="有序列表">1.</button>
      <span className="cs-toolbar-divider" />
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('formatBlock', '<h1>') }} title="标题 1">H1</button>
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('formatBlock', '<h2>') }} title="标题 2">H2</button>
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('formatBlock', '<h3>') }} title="标题 3">H3</button>
      <span className="cs-toolbar-divider" />
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('insertImageUrl') }} title="插入图片链接">图</button>
      <button type="button" onMouseDown={e => { e.preventDefault(); onAction('uploadImage') }} title="上传图片">上传</button>
    </div>
  )
}

export default function AdminPanel() {
  const navigate = useNavigate()
  const token = localStorage.getItem('token')
  const [tab, setTab] = useState('agents')
  const [agents, setAgents] = useState([])
  const [showAdd, setShowAdd] = useState(false)
  const [newUsername, setNewUsername] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newNickname, setNewNickname] = useState('')
  const [editAgent, setEditAgent] = useState(null)
  const [editNickname, setEditNickname] = useState('')
  const [editPassword, setEditPassword] = useState('')
  const [users, setUsers] = useState([])
  const [selectedUser, setSelectedUser] = useState(null)
  const [userMessages, setUserMessages] = useState([])
  const [welcomeHtml, setWelcomeHtml] = useState('')
  const [autoReplyHtml, setAutoReplyHtml] = useState('')
  const [settingsSaved, setSettingsSaved] = useState(false)
  const welcomeRef = useRef(null)
  const autoReplyRef = useRef(null)
  const fileInputRef = useRef(null)

  useEffect(() => {
    if (!token || localStorage.getItem('role') !== 'admin') {
      navigate('/admin/login')
      return
    }
    loadAgents()
    loadUsers()
    loadSettings()
  }, [])

  async function loadSettings() {
    try {
      const data = await request('/api/settings')
      if (data.welcome_message) setWelcomeHtml(data.welcome_message)
      if (data.auto_reply_message) setAutoReplyHtml(data.auto_reply_message)
      if (welcomeRef.current) welcomeRef.current.__loaded = false
      if (autoReplyRef.current) autoReplyRef.current.__loaded = false
    } catch (err) {
      console.error('加载系统设置失败', err)
    }
  }

  useEffect(() => {
    if (tab !== 'settings') return
    if (welcomeRef.current && welcomeHtml && !welcomeRef.current.__loaded) {
      welcomeRef.current.innerHTML = welcomeHtml
      welcomeRef.current.__loaded = true
    }
  }, [welcomeHtml, tab])

  useEffect(() => {
    if (tab !== 'settings') return
    if (autoReplyRef.current && autoReplyHtml && !autoReplyRef.current.__loaded) {
      autoReplyRef.current.innerHTML = autoReplyHtml
      autoReplyRef.current.__loaded = true
    }
  }, [autoReplyHtml, tab])

  async function saveSettings() {
    const h1 = welcomeRef.current?.innerHTML || ''
    const h2 = autoReplyRef.current?.innerHTML || ''
    try {
      await request('/api/settings', {
        method: 'PUT',
        body: JSON.stringify({ welcome_message: h1, auto_reply_message: h2 })
      })
      setSettingsSaved(true)
      setTimeout(() => setSettingsSaved(false), 2000)
    } catch (err) {
      console.error('保存系统设置失败', err)
    }
  }

  async function loadAgents() {
    try {
      const data = await request('/api/agent/list')
      setAgents(data)
    } catch (err) {
      console.error('加载客服列表失败', err)
    }
  }

  async function loadUsers() {
    try {
      const data = await request('/api/message/users?page=0&size=1000')
      setUsers(data)
    } catch (err) {
      console.error('加载用户列表失败', err)
    }
  }

  async function addAgent() {
    if (!newUsername || !newPassword) return
    try {
      await request('/api/agent/add', {
        method: 'POST',
        body: JSON.stringify({ username: newUsername, password: newPassword, nickname: newNickname || newUsername })
      })
      setShowAdd(false)
      setNewUsername('')
      setNewPassword('')
      setNewNickname('')
      loadAgents()
    } catch (err) {
      console.error('新增客服失败', err)
    }
  }

  async function updateAgent(id) {
    try {
      await request('/api/agent/update/' + id, {
        method: 'PUT',
        body: JSON.stringify({ nickname: editNickname, password: editPassword })
      })
      setEditAgent(null)
      loadAgents()
    } catch (err) {
      console.error('更新客服失败', err)
    }
  }

  async function toggleAgent(id, current) {
    try {
      await request('/api/agent/update/' + id, {
        method: 'PUT',
        body: JSON.stringify({ enabled: String(!current) })
      })
      loadAgents()
    } catch (err) {
      console.error('切换客服状态失败', err)
    }
  }

  async function deleteAgent(id) {
    if (!confirm('确定删除此客服？')) return
    try {
      await request('/api/agent/delete/' + id, { method: 'DELETE' })
      loadAgents()
    } catch (err) {
      console.error('删除客服失败', err)
    }
  }

  async function loadUserMessages(userId) {
    setSelectedUser(userId)
    try {
      const data = await request('/api/message/history/' + userId)
      setUserMessages(data)
    } catch (err) {
      console.error('加载聊天记录失败', err)
    }
  }

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('role')
    navigate('/admin/login')
  }

  function onAction(action, value) {
    const sel = window.getSelection()
    let editor = null
    if (sel && sel.rangeCount > 0) {
      const node = sel.getRangeAt(0).startContainer
      if (welcomeRef.current?.contains(node)) editor = welcomeRef.current
      else if (autoReplyRef.current?.contains(node)) editor = autoReplyRef.current
    }
    if (!editor) editor = welcomeRef.current
    if (!editor) return
    editor.focus()
    if (action === 'insertImageUrl') {
      const url = prompt('请输入图片链接')
      if (url) document.execCommand('insertImage', false, url)
    } else if (action === 'uploadImage') {
      fileInputRef.current.value = ''
      fileInputRef.current.onchange = () => {
        const file = fileInputRef.current.files?.[0]
        if (!file || !file.type.startsWith('image/')) return
        const fd = new FormData()
        fd.append('file', file)
        editor.focus()
        request('/api/message/upload', { method: 'POST', body: fd }).then(data => {
          if (data.url) document.execCommand('insertImage', false, data.url)
        }).catch(err => console.error('上传图片失败', err))
        fileInputRef.current.value = ''
      }
      fileInputRef.current.click()
    } else {
      document.execCommand(action, false, value || null)
    }
    editor.focus()
  }

  const visibleAgents = agents.filter(a => a.username !== 'admin')
  const onlineAgents = visibleAgents.filter(a => a.online).length
  const enabledAgents = visibleAgents.filter(a => a.enabled).length
  const unreadMessages = users.reduce((sum, u) => sum + (u.unread || 0), 0)
  const pageTitle = tab === 'agents' ? '客服管理' : tab === 'settings' ? '自动回复设置' : '聊天记录'
  const selectedUserAvatar = getUserAvatar(selectedUser)

  return (
    <div className="cs-admin">
      <aside className="cs-admin-sidebar">
        <div className="cs-admin-brand">
          <div className="cs-admin-logo">客</div>
          <div>
            <div className="cs-admin-title">客服管理系统</div>
            <div className="cs-admin-sub">管理后台</div>
          </div>
        </div>

        <nav className="cs-admin-nav">
          <button className={tab === 'agents' ? 'is-active' : ''} onClick={() => setTab('agents')}>客服管理</button>
          <button className={tab === 'settings' ? 'is-active' : ''} onClick={() => setTab('settings')}>自动回复</button>
          <button className={tab === 'history' ? 'is-active' : ''} onClick={() => setTab('history')}>聊天记录</button>
        </nav>

        <button className="cs-admin-logout" onClick={handleLogout}>退出登录</button>
      </aside>

      <main className="cs-admin-main">
        <header className="cs-admin-top">
          <div>
            <h1>{pageTitle}</h1>
            <p>在线客服系统运行与服务配置</p>
          </div>
          <button className="cs-admin-refresh" onClick={() => { loadAgents(); loadUsers(); }}>刷新数据</button>
        </header>

        <section className="cs-admin-metrics">
          <div className="cs-admin-metric"><b>{onlineAgents}</b><span>在线客服</span></div>
          <div className="cs-admin-metric"><b>{enabledAgents}</b><span>启用客服</span></div>
          <div className="cs-admin-metric"><b>{users.length}</b><span>会话用户</span></div>
          <div className="cs-admin-metric"><b>{unreadMessages}</b><span>未读消息</span></div>
        </section>

        {tab === 'agents' && (
          <section className="cs-panel">
            <div className="cs-panel-head">
              <div>
                <h2>客服列表</h2>
                <p>管理客服账号、昵称、启用状态与在线状态。</p>
              </div>
              <button className="cs-primary" onClick={() => setShowAdd(true)}>新增客服</button>
            </div>

            <div className="cs-table-wrap">
              <table className="cs-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>用户名</th>
                    <th>昵称</th>
                    <th>状态</th>
                    <th>在线</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleAgents.map(a => (
                    <tr key={a.id}>
                      <td>{a.id}</td>
                      <td>{a.username}</td>
                      <td>
                        {editAgent === a.id
                          ? <input className="cs-inline-input" value={editNickname} onChange={e => setEditNickname(e.target.value)} />
                          : a.nickname}
                      </td>
                      <td><span className={a.enabled ? 'cs-status-pill is-success' : 'cs-status-pill is-danger'}>{a.enabled ? '正常' : '已禁用'}</span></td>
                      <td><span className={a.online ? 'cs-status-pill is-success' : 'cs-muted-pill'}>{a.online ? '在线' : '离线'}</span></td>
                      <td>
                        <div className="cs-actions">
                          {editAgent === a.id ? (
                            <>
                              <input className="cs-inline-input" type="password" value={editPassword} onChange={e => setEditPassword(e.target.value)} placeholder="新密码" />
                              <button className="cs-btn green" onClick={() => updateAgent(a.id)}>保存</button>
                              <button className="cs-btn" onClick={() => setEditAgent(null)}>取消</button>
                            </>
                          ) : (
                            <button className="cs-btn blue" onClick={() => { setEditAgent(a.id); setEditNickname(a.nickname || ''); setEditPassword('') }}>编辑</button>
                          )}
                          <button className={a.enabled ? 'cs-btn orange' : 'cs-btn green'} onClick={() => toggleAgent(a.id, a.enabled)}>{a.enabled ? '禁用' : '启用'}</button>
                          <button className="cs-btn red" onClick={() => deleteAgent(a.id)}>删除</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {tab === 'settings' && (
          <section className="cs-panel cs-settings-panel">
            <div className="cs-panel-head" style={{ padding: 0, paddingBottom: 18, marginBottom: 20 }}>
              <div>
                <h2>自动回复设置</h2>
                <p>配置欢迎语和离线自动回复，支持富文本内容。</p>
              </div>
              {settingsSaved && <span className="cs-saved">保存成功</span>}
            </div>

            <div className="cs-editor-block">
              <label className="cs-editor-label">欢迎语</label>
              <Toolbar onAction={onAction} />
              <div ref={welcomeRef} className="cs-editor" contentEditable suppressContentEditableWarning />
            </div>

            <div className="cs-editor-block">
              <label className="cs-editor-label">离线自动回复</label>
              <Toolbar onAction={onAction} />
              <div ref={autoReplyRef} className="cs-editor" contentEditable suppressContentEditableWarning />
            </div>

            <button className="cs-primary" onClick={saveSettings}>保存设置</button>
          </section>
        )}

        {tab === 'history' && (
          <section className="cs-history">
            <aside className="cs-history-list">
              <div className="cs-history-list-title">用户列表</div>
              {users.map(u => (
                <button key={u.userId} onClick={() => loadUserMessages(u.userId)} className={'cs-history-item' + (selectedUser === u.userId ? ' is-active' : '')}>
                  <img className="cs-history-avatar" src={getUserAvatar(u.userId)} alt="" />
                  <span style={{ minWidth: 0 }}>
                    <span className="cs-history-name">{u.nickname || u.userId}</span>
                    <span className="cs-history-preview">{u.lastMessage || '暂无消息'}</span>
                  </span>
                </button>
              ))}
            </aside>

            <div className="cs-history-chat">
              {!selectedUser ? (
                <div className="cs-history-empty">选择一个用户查看聊天记录</div>
              ) : (
                <>
                  <div className="cs-history-chat-head">
                    聊天记录 - {users.find(u => u.userId === selectedUser)?.nickname || selectedUser}
                    <span>只读</span>
                  </div>
                  <div className="cs-history-messages">
                    {userMessages.map((msg, i) => {
                      const isUser = msg.direction === 'user'
                      return (
                        <div key={i} className={'cs-history-row' + (isUser ? '' : ' is-agent')}>
                          {isUser && <img className="cs-message-avatar" src={selectedUserAvatar} alt="" />}
                          <div className="cs-history-bubble">
                            <div className="cs-msg-role">{isUser ? '用户' : '客服'}</div>
                            {msg.msgType === 'image' ? (
                              <img src={'' + msg.fileUrl} alt="" className="cs-history-image" onClick={() => window.open('' + msg.fileUrl)} />
                            ) : msg.msgType === 'file' ? (
                              <a href={'' + msg.fileUrl} target="_blank" rel="noreferrer" style={{ color: '#2563eb' }}>{msg.content}</a>
                            ) : (
                              <span dangerouslySetInnerHTML={{ __html: msg.content }} />
                            )}
                            <div className="cs-msg-time">{formatTime(msg.createdAt)}</div>
                          </div>
                          {!isUser && <img className="cs-message-avatar" src={customerServiceAvatar} alt="" />}
                        </div>
                      )
                    })}
                  </div>
                </>
              )}
            </div>
          </section>
        )}
      </main>

      {showAdd && (
        <div className="cs-modal-mask">
          <div className="cs-modal">
            <div className="cs-modal-head">
              <h3>新增客服</h3>
              <button className="cs-close" onClick={() => setShowAdd(false)}>×</button>
            </div>
            <label className="cs-field">
              <span>用户名</span>
              <input className="cs-input" value={newUsername} onChange={e => setNewUsername(e.target.value)} placeholder="登录账号" />
            </label>
            <label className="cs-field">
              <span>密码</span>
              <input className="cs-input" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="登录密码" />
            </label>
            <label className="cs-field">
              <span>昵称</span>
              <input className="cs-input" value={newNickname} onChange={e => setNewNickname(e.target.value)} placeholder="显示名称" />
            </label>
            <div className="cs-modal-actions">
              <button className="cs-secondary" onClick={() => setShowAdd(false)}>取消</button>
              <button className="cs-primary" onClick={addAgent}>确定</button>
            </div>
          </div>
        </div>
      )}

      <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }} />
    </div>
  )
}
