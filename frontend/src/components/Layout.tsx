import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { fetchHealth, type HealthResponse } from '../api/client'

function Logo() {
  return (
    <svg width="30" height="30" viewBox="0 0 28 28" aria-hidden="true">
      <rect width="28" height="28" rx="7" fill="#2B2F77" />
      <path d="M7.5 9.2c2.2-.9 4.4-.8 6.5.7 2.1-1.5 4.3-1.6 6.5-.7v9.6c-2.2-.9-4.4-.8-6.5.7-2.1-1.5-4.3-1.6-6.5-.7z" fill="none" stroke="#FFFFFF" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M14 9.9v9.6" stroke="#FFFFFF" strokeWidth="1.6" />
      <circle cx="21.5" cy="6.5" r="2.5" fill="#8C7CF0" />
    </svg>
  )
}

function SystemStatus() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    fetchHealth().then(setHealth).catch(() => setFailed(true))
  }, [])

  const backend = failed ? 'down' : health ? 'up' : ''
  const db = health ? (health.database === 'UP' ? 'up' : 'down') : failed ? 'down' : ''
  return (
    <div className="status" aria-label="System status">
      <span><span className={`dot ${backend}`} />Backend {failed ? 'unreachable' : health ? 'up' : 'checking…'}</span>
      <span><span className={`dot ${db}`} />Database {db === 'up' ? 'up' : db === 'down' ? 'down' : 'checking…'}</span>
    </div>
  )
}

export default function Layout() {
  return (
    <div className="shell">
      <nav className="sidebar" aria-label="Primary">
        <div className="brand"><Logo /> ResearchMate AI</div>
        <NavLink to="/discover" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
          Discover Papers
        </NavLink>
        <NavLink to="/library" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z" /></svg>
          My Library
        </NavLink>
        <NavLink to="/upload" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 16V4m0 0-4 4m4-4 4 4M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" /></svg>
          Upload Papers
        </NavLink>
        <SystemStatus />
      </nav>
      <header className="topbar">
        <div className="topbar-brand"><Logo /> ResearchMate AI</div>
        <nav className="topnav" aria-label="Primary (mobile)">
          <NavLink to="/discover" className={({ isActive }) => `topnav-link${isActive ? ' active' : ''}`}>Discover</NavLink>
          <NavLink to="/library" className={({ isActive }) => `topnav-link${isActive ? ' active' : ''}`}>Library</NavLink>
          <NavLink to="/upload" className={({ isActive }) => `topnav-link${isActive ? ' active' : ''}`}>Upload</NavLink>
        </nav>
      </header>
      <main className="main">
        <div className="content"><Outlet /></div>
      </main>
    </div>
  )
}
