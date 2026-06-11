import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { request } from '../utils/api.js'

export default function AgentLogin() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function handleLogin(e) {
    e.preventDefault()
    setError('')
    try {
      const data = await request('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      })
      if (data.role === 'agent') {
        localStorage.setItem('token', data.token)
        localStorage.setItem('agentId', data.agentId)
        localStorage.setItem('username', data.username)
        localStorage.setItem('nickname', data.nickname || data.username)
        navigate('/agent')
      } else if (data.role === 'admin') {
        setError('请使用管理员登录入口')
      } else {
        setError('用户名或密码错误')
      }
    } catch {
      setError('登录失败，请检查网络')
    }
  }

  return (
    <div style={{ display: 'flex', height: '100vh', justifyContent: 'center', alignItems: 'center', background: '#f0f2f5' }}>
      <div style={{ background: '#fff', padding: '40px', borderRadius: 12, boxShadow: '0 2px 12px rgba(0,0,0,0.1)', width: 380 }}>
        <h2 style={{ textAlign: 'center', marginBottom: 24, color: '#333' }}>客服登录</h2>
        <form onSubmit={handleLogin}>
          <div style={{ marginBottom: 16 }}>
            <input style={inputStyle} type="text" placeholder="用户名" value={username} onChange={e => setUsername(e.target.value)} required />
          </div>
          <div style={{ marginBottom: 20 }}>
            <input style={inputStyle} type="password" placeholder="密码" value={password} onChange={e => setPassword(e.target.value)} required />
          </div>
          {error && <div style={{ color: '#f44336', marginBottom: 12, textAlign: 'center', fontSize: 14 }}>{error}</div>}
          <button type="submit" style={{ ...btnStyle, background: '#4A90D9' }}>登 录</button>
        </form>
        <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: '#999' }}>
          <a href="/admin/login" style={{ color: '#4A90D9', textDecoration: 'none' }}>管理员登录</a>
          <span style={{ margin: '0 10px' }}>|</span>
          <a href="/" style={{ color: '#4A90D9', textDecoration: 'none' }}>用户页面</a>
        </div>
      </div>
    </div>
  )
}

const inputStyle = {
  width: '100%', padding: '12px 16px', border: '1px solid #ddd', borderRadius: 8, fontSize: 15, outline: 'none', boxSizing: 'border-box',
}
const btnStyle = {
  width: '100%', padding: '12px', border: 'none', borderRadius: 8, fontSize: 16, color: '#fff', cursor: 'pointer',
}
