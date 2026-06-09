import { Routes, Route, Navigate } from 'react-router-dom'
import UserChat from './pages/UserChat.jsx'
import AgentLogin from './pages/AgentLogin.jsx'
import AgentPanel from './pages/AgentPanel.jsx'
import AdminPanel from './pages/AdminPanel.jsx'
import AdminLogin from './pages/AdminLogin.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<UserChat />} />
      <Route path="/agent/login" element={<AgentLogin />} />
      <Route path="/agent" element={<AgentPanel />} />
      <Route path="/admin/login" element={<AdminLogin />} />
      <Route path="/admin" element={<AdminPanel />} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  )
}
