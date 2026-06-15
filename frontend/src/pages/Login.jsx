import { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { request } from '../utils/api.js'

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const initialRole = useMemo(
    () => location.pathname.startsWith('/admin') ? 'admin' : 'agent',
    [location.pathname]
  )

  const [role, setRole] = useState(initialRole)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleLogin(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await request('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: username.trim(), password }),
      })

      if (role === 'admin' && data.role !== 'admin') {
        setError('当前账号不是管理员账号')
        return
      }
      if (role === 'agent' && data.role !== 'agent') {
        setError('当前账号不是客服账号')
        return
      }

      localStorage.setItem('token', data.token)
      localStorage.setItem('role', data.role)
      localStorage.setItem('username', data.username || username.trim())

      if (data.role === 'admin') {
        localStorage.removeItem('agentId')
        localStorage.removeItem('nickname')
        navigate('/admin')
      } else {
        localStorage.setItem('agentId', data.agentId)
        localStorage.setItem('nickname', data.nickname || data.username)
        navigate('/agent')
      }
    } catch {
      setError('登录失败，请检查用户名和密码')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.shell}>
        <section style={styles.loginPanel}>
          <div style={styles.loginHeader}>
            <h1>在线客服系统</h1>
          </div>

          <div style={styles.segment}>
            <button
              type="button"
              onClick={() => setRole('agent')}
              style={role === 'agent' ? styles.segmentActive : styles.segmentBtn}
            >
              客服
            </button>
            <button
              type="button"
              onClick={() => setRole('admin')}
              style={role === 'admin' ? styles.segmentActive : styles.segmentBtn}
            >
              管理员
            </button>
          </div>

          <form onSubmit={handleLogin} style={styles.form}>
            <label style={styles.field}>
              <span>用户名</span>
              <input
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder={role === 'admin' ? '请输入管理员账号' : '请输入客服账号'}
                autoComplete="username"
                required
                style={styles.input}
              />
            </label>

            <label style={styles.field}>
              <span>密码</span>
              <input
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="请输入密码"
                type="password"
                autoComplete="current-password"
                required
                style={styles.input}
              />
            </label>

            {error && <div style={styles.error}>{error}</div>}

            <button type="submit" disabled={loading} style={{
              ...styles.submitBtn,
              opacity: loading ? 0.72 : 1,
              cursor: loading ? 'not-allowed' : 'pointer',
            }}>
              {loading ? '登录中...' : '登录'}
            </button>
          </form>

        </section>
      </div>
    </div>
  )
}

const styles = {
  page: {
    minHeight: '100vh',
    background: '#F6F8FB',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
    boxSizing: 'border-box',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif',
    color: '#0F172A',
  },
  shell: {
    width: 'min(520px, 100%)',
    background: '#FFFFFF',
    border: '1px solid #E5E7EB',
    borderRadius: 8,
    boxShadow: '0 18px 50px rgba(15, 23, 42, 0.10)',
    overflow: 'hidden',
  },
  loginPanel: { padding: 40, display: 'flex', flexDirection: 'column', justifyContent: 'center' },
  loginHeader: { marginBottom: 24, textAlign: 'center' },
  segment: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 4,
    background: '#F1F5F9',
    padding: 4,
    borderRadius: 8,
    marginBottom: 24,
  },
  segmentBtn: {
    border: 'none',
    borderRadius: 6,
    background: 'transparent',
    color: '#64748B',
    fontSize: 14,
    fontWeight: 600,
    padding: '10px 12px',
    cursor: 'pointer',
  },
  segmentActive: {
    border: 'none',
    borderRadius: 6,
    background: '#FFFFFF',
    color: '#2563EB',
    fontSize: 14,
    fontWeight: 700,
    padding: '10px 12px',
    cursor: 'pointer',
    boxShadow: '0 1px 4px rgba(15, 23, 42, 0.10)',
  },
  form: { display: 'flex', flexDirection: 'column', gap: 16 },
  field: { display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13, color: '#475569', fontWeight: 600 },
  input: {
    height: 44,
    border: '1px solid #D1D5DB',
    borderRadius: 8,
    padding: '0 12px',
    fontSize: 14,
    outline: 'none',
    boxSizing: 'border-box',
  },
  error: {
    background: '#FEF2F2',
    color: '#DC2626',
    border: '1px solid #FECACA',
    borderRadius: 8,
    padding: '10px 12px',
    fontSize: 13,
  },
  submitBtn: {
    height: 44,
    border: 'none',
    borderRadius: 8,
    background: '#2563EB',
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: 700,
  },
}
