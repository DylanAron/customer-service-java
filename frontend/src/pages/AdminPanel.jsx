import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { request } from '../utils/api.js'

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
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
  // 设置
  const [welcomeHtml, setWelcomeHtml] = useState('')
  const [autoReplyHtml, setAutoReplyHtml] = useState('')
  const [settingsSaved, setSettingsSaved] = useState(false)
  const welcomeRef = useRef(null)
  const autoReplyRef = useRef(null)
  const fileInputRef = useRef(null)

  useEffect(() => {
    if (!token) { navigate('/admin/login'); return }
    loadAgents()
    loadUsers()
    loadSettings()
  }, [])

  async function loadSettings() {
    try {
      const data = await request('/api/settings')
      if (data.welcome_message) setWelcomeHtml(data.welcome_message)
      if (data.auto_reply_message) setAutoReplyHtml(data.auto_reply_message)
      // 重置加载标记，让下次切到 settings tab 时重新填入
      if (welcomeRef.current) welcomeRef.current.__loaded = false
      if (autoReplyRef.current) autoReplyRef.current.__loaded = false
    } catch {}
  }

  // 数据加载后填入 contentEditable（在 tab 切换到 settings 时也要触发）
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
    } catch {}
  }

  async function loadAgents() {
    try { const data = await request('/api/agent/list'); setAgents(data) } catch {}
  }
  async function loadUsers() {
    try { const data = await request('/api/message/users?page=0&size=1000'); setUsers(data) } catch {}
  }
  async function addAgent() {
    if (!newUsername || !newPassword) return
    try {
      await request('/api/agent/add', { method: 'POST', body: JSON.stringify({ username: newUsername, password: newPassword, nickname: newNickname || newUsername }) })
      setShowAdd(false); setNewUsername(''); setNewPassword(''); setNewNickname(''); loadAgents()
    } catch {}
  }
  async function updateAgent(id) {
    try {
      await request('/api/agent/update/' + id, { method: 'PUT', body: JSON.stringify({ nickname: editNickname, password: editPassword }) })
      setEditAgent(null); loadAgents()
    } catch {}
  }
  async function toggleAgent(id, current) {
    try {
      await request('/api/agent/update/' + id, { method: 'PUT', body: JSON.stringify({ enabled: String(!current) }) })
      loadAgents()
    } catch {}
  }
  async function deleteAgent(id) {
    if (!confirm('确定删除此客服？')) return
    try { await request('/api/agent/delete/' + id, { method: 'DELETE' }); loadAgents() } catch {}
  }
  async function loadUserMessages(userId) {
    setSelectedUser(userId)
    try { const data = await request('/api/message/history/' + userId); setUserMessages(data) } catch {}
  }
  function handleLogout() {
    localStorage.removeItem('token'); localStorage.removeItem('role'); navigate('/admin/login')
  }

  function onAction(action, value) {
    // 选择当前活跃的编辑器
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
        }).catch(() => {})
        fileInputRef.current.value = ''
      }
      fileInputRef.current.click()
    } else {
      document.execCommand(action, false, value || null)
    }
    editor.focus()
  }

  function Toolbar() {
    return (
      <div style={styles.toolbar}>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('bold') }} title="加粗"><b>B</b></button>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('italic') }} title="斜体"><i>I</i></button>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('underline') }} title="下划线"><u>U</u></button>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('strikeThrough') }} title="删除线"><s>S</s></button>
        <span style={{ width: 1, height: 20, background: '#ddd', margin: '0 4px' }} />
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('insertUnorderedList') }} title="无序列表">•</button>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('insertOrderedList') }} title="有序列表">1.</button>
        <span style={{ width: 1, height: 20, background: '#ddd', margin: '0 4px' }} />
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('formatBlock', '<h1>') }} title="标题1">H1</button>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('formatBlock', '<h2>') }} title="标题2">H2</button>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('formatBlock', '<h3>') }} title="标题3">H3</button>
        <span style={{ width: 1, height: 20, background: '#ddd', margin: '0 4px' }} />
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('insertImageUrl') }} title="插入图片链接">🖼</button>
        <button type="button" style={styles.tbBtn} onMouseDown={e => { e.preventDefault(); onAction('uploadImage') }} title="上传图片">⬆</button>
      </div>
    )
  }

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      <div style={{ background: '#263238', color: '#fff', padding: '12px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ margin: 0, fontSize: 18 }}>客服管理系统</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => setTab('agents')} style={tabBtnStyle(tab === 'agents')}>客服管理</button>
          <button onClick={() => setTab('settings')} style={tabBtnStyle(tab === 'settings')}>自动回复</button>
          <button onClick={() => setTab('history')} style={tabBtnStyle(tab === 'history')}>聊天记录</button>
          <button onClick={handleLogout} style={{ ...tabBtnStyle(false), color: '#FF8A65' }}>退出</button>
        </div>
      </div>

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* === 客服管理 Tab === */}
        {tab === 'agents' && (
          <div style={{ flex: 1, padding: 24, overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16 }}>客服列表</h3>
              <button onClick={() => setShowAdd(true)} style={primaryBtn}>新增客服</button>
            </div>
            <table style={{ width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: 8, overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.1)' }}>
              <thead><tr style={{ background: '#F5F5F5' }}>
                <th style={thStyle}>ID</th><th style={thStyle}>用户名</th><th style={thStyle}>昵称</th>
                <th style={thStyle}>状态</th><th style={thStyle}>在线</th><th style={thStyle}>操作</th>
              </tr></thead>
              <tbody>
                {agents.filter(a => a.username !== 'admin').map(a => (
                  <tr key={a.id} style={{ borderBottom: '1px solid #F0F0F0' }}>
                    <td style={tdStyle}>{a.id}</td>
                    <td style={tdStyle}>{a.username}</td>
                    <td style={tdStyle}>{editAgent === a.id ? <input value={editNickname} onChange={e => setEditNickname(e.target.value)} style={smInput} /> : a.nickname}</td>
                    <td style={tdStyle}><span style={{ color: a.enabled ? '#4CAF50' : '#f44336', fontSize: 13 }}>{a.enabled ? '正常' : '已禁用'}</span></td>
                    <td style={tdStyle}><span style={{ color: a.online ? '#4CAF50' : '#999', fontSize: 13 }}>{a.online ? '在线' : '离线'}</span></td>
                    <td style={tdStyle}>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        {editAgent === a.id ? (
                          <>
                            <input type="password" value={editPassword} onChange={e => setEditPassword(e.target.value)} placeholder="新密码" style={smInput} />
                            <button onClick={() => updateAgent(a.id)} style={smGreenBtn}>保存</button>
                            <button onClick={() => setEditAgent(null)} style={smGrayBtn}>取消</button>
                          </>
                        ) : (
                          <button onClick={() => { setEditAgent(a.id); setEditNickname(a.nickname || ''); setEditPassword('') }} style={smBlueBtn}>编辑</button>
                        )}
                        <button onClick={() => toggleAgent(a.id, a.enabled)} style={a.enabled ? smOrangeBtn : smGreenBtn}>{a.enabled ? '禁用' : '启用'}</button>
                        <button onClick={() => deleteAgent(a.id)} style={smRedBtn}>删除</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {showAdd && (
              <div style={modalOverlay}>
                <div style={modalBox}>
                  <h3 style={{ marginBottom: 16 }}>新增客服</h3>
                  <div style={{ marginBottom: 12 }}><label style={label}>用户名</label><input style={modalInput} value={newUsername} onChange={e => setNewUsername(e.target.value)} placeholder="登录账号" /></div>
                  <div style={{ marginBottom: 12 }}><label style={label}>密码</label><input style={modalInput} type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="登录密码" /></div>
                  <div style={{ marginBottom: 20 }}><label style={label}>昵称</label><input style={modalInput} value={newNickname} onChange={e => setNewNickname(e.target.value)} placeholder="显示名称" /></div>
                  <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
                    <button onClick={() => setShowAdd(false)} style={{ padding: '8px 20px', border: '1px solid #ddd', borderRadius: 4, background: '#fff', cursor: 'pointer' }}>取消</button>
                    <button onClick={addAgent} style={{ padding: '8px 20px', background: '#4A90D9', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer' }}>确定</button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* === 自动回复设置 Tab === */}
        {tab === 'settings' && (
          <div style={{ flex: 1, padding: 24, overflowY: 'auto', maxWidth: 700 }}>
            <h3 style={{ marginBottom: 6, fontSize: 16 }}>自动回复设置</h3>
            <p style={{ fontSize: 13, color: '#999', marginBottom: 20 }}>
              设置欢迎语和自动回复内容，修改后点击保存即可生效。
            </p>

            <div style={{ marginBottom: 24 }}>
              <label style={{ fontSize: 14, fontWeight: 600, color: '#333', display: 'block', marginBottom: 8 }}>
                欢迎语
                <span style={{ fontSize: 12, color: '#999', fontWeight: 400, marginLeft: 8 }}>用户进入聊天时自动发送（支持富文本）</span>
              </label>
              <Toolbar />
              <div
                ref={welcomeRef}
                contentEditable
                suppressContentEditableWarning
                style={styles.editor}
              />
            </div>

            <div style={{ marginBottom: 24 }}>
              <label style={{ fontSize: 14, fontWeight: 600, color: '#333', display: 'block', marginBottom: 8 }}>
                离线自动回复
                <span style={{ fontSize: 12, color: '#999', fontWeight: 400, marginLeft: 8 }}>无在线客服时，用户发送消息自动回复此内容（支持富文本）</span>
              </label>
              <Toolbar />
              <div
                ref={autoReplyRef}
                contentEditable
                suppressContentEditableWarning
                style={styles.editor}
              />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <button onClick={saveSettings} style={primaryBtn}>保存设置</button>
              {settingsSaved && <span style={{ color: '#4CAF50', fontSize: 14 }}>✓ 保存成功</span>}
            </div>
          </div>
        )}

        {/* === 聊天记录 Tab === */}
        {tab === 'history' && (
          <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
            <div style={{ width: 280, borderRight: '1px solid #E0E0E0', overflowY: 'auto', background: '#FAFAFA' }}>
              <div style={{ padding: '12px 16px', fontSize: 14, fontWeight: 600, borderBottom: '1px solid #E0E0E0' }}>用户列表</div>
              {users.map(u => (
                <div key={u.userId} onClick={() => loadUserMessages(u.userId)}
                  style={{ padding: '10px 16px', cursor: 'pointer', borderBottom: '1px solid #F0F0F0', background: selectedUser === u.userId ? '#E3F2FD' : 'transparent', fontSize: 14 }}>
                  <div>{u.nickname || u.userId}</div>
                  <div style={{ fontSize: 12, color: '#999', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{u.lastMessage || '暂无消息'}</div>
                </div>
              ))}
            </div>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: '#F5F5F5' }}>
              {!selectedUser ? (
                <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>选择一个用户查看聊天记录</div>
              ) : (
                <>
                  <div style={{ padding: '12px 20px', background: '#fff', borderBottom: '1px solid #E0E0E0', fontSize: 14, fontWeight: 600, color: '#666' }}>
                    聊天记录 - {users.find(u => u.userId === selectedUser)?.nickname || selectedUser}
                    <span style={{ fontSize: 12, color: '#999', fontWeight: 400, marginLeft: 8 }}>（只读）</span>
                  </div>
                  <div style={{ flex: 1, overflowY: 'auto', padding: 20 }}>
                    {userMessages.map((msg, i) => (
                      <div key={i} style={{ display: 'flex', justifyContent: msg.direction === 'user' ? 'flex-start' : 'flex-end', marginBottom: 12 }}>
                        <div style={{ maxWidth: '60%', padding: '10px 16px', borderRadius: 8, background: msg.direction === 'user' ? '#fff' : '#E8F5E9', color: '#333', boxShadow: '0 1px 3px rgba(0,0,0,0.1)', fontSize: 14, lineHeight: 1.5, wordBreak: 'break-word' }}>
                          <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>{msg.direction === 'user' ? '用户' : '客服'}</div>
                          {msg.msgType === 'image' ? (
                            <img src={'' + msg.fileUrl} alt="" style={{ maxWidth: 200, borderRadius: 4 }} onClick={() => window.open('' + msg.fileUrl)} />
                          ) : msg.msgType === 'file' ? (
                            <div><span>📎</span><a href={'' + msg.fileUrl} target="_blank" rel="noreferrer" style={{ color: '#4A90D9', marginLeft: 4 }}>{msg.content}</a></div>
                          ) : (
                            <span dangerouslySetInnerHTML={{ __html: msg.content }} />
                          )}
                          <div style={{ fontSize: 11, color: '#999', marginTop: 4, textAlign: 'right' }}>{formatTime(msg.createdAt)}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 隐藏的文件上传 input */}
      <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }} />
    </div>
  )
}

/* ============ 样式 ============ */
const primaryBtn = { padding: '8px 20px', background: '#4A90D9', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 14 }
const thStyle = { padding: '12px 16px', textAlign: 'left', fontSize: 13, color: '#666', fontWeight: 600 }
const tdStyle = { padding: '10px 16px', fontSize: 14 }
const smInput = { width: 100, padding: '4px 8px', border: '1px solid #ddd', borderRadius: 3, fontSize: 12, outline: 'none' }
const smBlueBtn = { padding: '4px 10px', background: '#E3F2FD', color: '#1565C0', border: '1px solid #BBDEFB', borderRadius: 3, cursor: 'pointer', fontSize: 12 }
const smGreenBtn = { padding: '4px 10px', background: '#E8F5E9', color: '#2E7D32', border: '1px solid #C8E6C9', borderRadius: 3, cursor: 'pointer', fontSize: 12 }
const smOrangeBtn = { padding: '4px 10px', background: '#FFF3E0', color: '#E65100', border: '1px solid #FFE0B2', borderRadius: 3, cursor: 'pointer', fontSize: 12 }
const smRedBtn = { padding: '4px 10px', background: '#FFEBEE', color: '#C62828', border: '1px solid #FFCDD2', borderRadius: 3, cursor: 'pointer', fontSize: 12 }
const smGrayBtn = { padding: '4px 10px', background: '#F5F5F5', color: '#666', border: '1px solid #ddd', borderRadius: 3, cursor: 'pointer', fontSize: 12 }

const styles = {
  toolbar: {
    display: 'flex', gap: 4, padding: '6px 8px',
    background: '#f5f5f5', border: '1px solid #ddd',
    borderBottom: 'none', borderRadius: '8px 8px 0 0',
    flexWrap: 'wrap', alignItems: 'center',
  },
  tbBtn: {
    padding: '2px 10px', border: '1px solid #ccc',
    borderRadius: 4, background: '#fff', cursor: 'pointer',
    fontSize: 13, minWidth: 30, userSelect: 'none',
  },
  editor: {
    width: '100%', minHeight: 100, padding: '12px 14px',
    border: '1px solid #ddd', borderRadius: '0 0 8px 8px',
    borderTop: 'none',
    fontSize: 14, outline: 'none', boxSizing: 'border-box',
    lineHeight: 1.6, fontFamily: 'inherit',
    background: '#fff', overflowY: 'auto',
    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
    overflowWrap: 'break-word', cursor: 'text', color: '#333',
  },
}

function tabBtnStyle(active) {
  return { padding: '6px 16px', background: active ? 'rgba(255,255,255,0.15)' : 'transparent', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 14 }
}

const modalOverlay = { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }
const modalBox = { background: '#fff', borderRadius: 12, padding: 24, width: 400, boxShadow: '0 4px 20px rgba(0,0,0,0.15)' }
const modalInput = { width: '100%', padding: '10px 12px', border: '1px solid #ddd', borderRadius: 6, fontSize: 14, outline: 'none', boxSizing: 'border-box' }
const label = { fontSize: 13, color: '#666', display: 'block', marginBottom: 4 }
