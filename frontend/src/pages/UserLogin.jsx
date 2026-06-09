import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { request } from '../utils/api.js'

export default function UserLogin() {
  const [tab, setTab] = useState('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    try {
      const url = tab === 'login' ? '/api/user/login' : '/api/user/register'
      const body = { username, password }
      if (tab === 'register') body.nickname = nickname || username

      const data = await request(url, { method: 'POST', body: JSON.stringify(body) })
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('cs_username', data.username)
      localStorage.setItem('cs_nickname', data.nickname || data.username)
      navigate('/chat')
    } catch (e) {
      setError(tab === 'login' ? '用户名或密码错误' : '注册失败，可能用户名已存在')
    }
  }

  return (
    <div style={{
      display: 'flex', height: '100vh', justifyContent: 'center', alignItems: 'center',
      background: '#f0f2f5', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    }}>
      <div style={{
        background: '#fff', padding: '36px 32px', borderRadius: 16, boxShadow: '0 4px 20px rgba(0,0,0,0.1)',
        width: 360
      }}>
        <h2 style={{ textAlign: 'center', marginBottom: 8, color: '#333', fontSize: 22 }}>客服系统</h2>
        <p style={{ textAlign: 'center', color: '#999', fontSize: 13, marginBottom: 24 }}>欢迎使用在线客服</p>

        {/* Tab switch */}
        <div style={{ display: 'flex', marginBottom: 24, background: '#F5F5F5', borderRadius: 8, padding: 3 }}>
          <div
            onClick={() => setTab('login')}
            style={{
              flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: 6, cursor: 'pointer',
              background: tab === 'login' ? '#fff' : 'transparent', fontWeight: tab === 'login' ? 600 : 400,
              fontSize: 14, color: tab === 'login' ? '#4A90D9' : '#666', transition: 'all 0.2s'
            }}
          >登录</div>
          <div
            onClick={() => setTab('register')}
            style={{
              flex: 1, textAlign: 'center', padding: '8px 0', borderRadius: 6, cursor: 'pointer',
              background: tab === 'register' ? '#fff' : 'transparent', fontWeight: tab === 'register' ? 600 : 400,
              fontSize: 14, color: tab === 'register' ? '#4A90D9' : '#666', transition: 'all 0.2s'
            }}
          >注册</div>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: 13, color: '#666', display: 'block', marginBottom: 4 }}>用户名</label>
            <input
              style={inputStyle}
              type="text" placeholder="请输入用户名" value={username}
              onChange={e => setUsername(e.target.value)} required
            />
          </div>
          <div style={{ marginBottom: tab === 'register' ? 14 : 20 }}>
            <label style={{ fontSize: 13, color: '#666', display: 'block', marginBottom: 4 }}>密码</label>
            <input
              style={inputStyle}
              type="password" placeholder="请输入密码" value={password}
              onChange={e => setPassword(e.target.value)} required
            />
          </div>
          {tab === 'register' && (
            <div style={{ marginBottom: 20 }}>
              <label style={{ fontSize: 13, color: '#666', display: 'block', marginBottom: 4 }}>昵称（选填）</label>
              <input
                style={inputStyle}
                type="text" placeholder="显示名称" value={nickname}
                onChange={e => setNickname(e.target.value)}
              />
            </div>
          )}
          {error && <div style={{ color: '#f44336', marginBottom: 12, textAlign: 'center', fontSize: 13 }}>{error}</div>}
          <button type="submit" style={{
            width: '100%', padding: '12px', border: 'none', borderRadius: 8,
            fontSize: 16, color: '#fff', cursor: 'pointer',
            background: tab === 'login' ? '#4A90D9' : '#4CAF50'
          }}>
            {tab === 'login' ? '登 录' : '注 册'}
          </button>
        </form>

        <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: '#999' }}>
          客服？
          <a href="/agent/login" style={{ color: '#4A90D9', textDecoration: 'none', marginLeft: 4 }}>客服登录</a>
          <span style={{ margin: '0 10px' }}>|</span>
          <a href="/admin/login" style={{ color: '#4A90D9', textDecoration: 'none' }}>管理员</a>
        </div>
      </div>
    </div>
  )
}

const inputStyle = {
  width: '100%', padding: '11px 14px', border: '1px solid #ddd', borderRadius: 8,
  fontSize: 14, outline: 'none', boxSizing: 'border-box', background: '#FAFAFA'
}
