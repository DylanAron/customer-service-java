import { Routes, Route, Navigate } from 'react-router-dom'
import UserChat from './pages/UserChat.jsx'
import Login from './pages/Login.jsx'
import AgentPanel from './pages/AgentPanel.jsx'
import AdminPanel from './pages/AdminPanel.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<UserChat />} />
      <Route path="/login" element={<Login />} />
      <Route path="/agent/login" element={<Login />} />
      <Route path="/agent" element={<AgentPanel />} />
      <Route path="/admin/login" element={<Login />} />
      <Route path="/admin" element={<AdminPanel />} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  )
}
