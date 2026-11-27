import { useState } from 'react'
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { ROLE_LABEL } from './Display.jsx'
import { ForbiddenState } from './States.jsx'

/** Nav items and their gates (frontend/SPEC.md §2). UX only — the backend enforces every permission. */
export const NAV = [
  { to: '/claims', label: 'My claims' },
  { to: '/approvals', label: 'Approvals', gate: { capabilities: ['isApprover'] } },
  { to: '/review-queue', label: 'Review queue', gate: { roles: ['FINANCE_AUDITOR'] } },
  { to: '/delegations', label: 'Delegations', gate: { capabilities: ['canDelegate'] } },
  { to: '/payout-batches', label: 'Payouts', gate: { capabilities: ['isAdmin'] } },
  { to: '/dashboard', label: 'Dashboard', gate: { roles: ['FINANCE_AUDITOR'], capabilities: ['isAdmin'] } },
  { to: '/audit-trail', label: 'Audit trail', gate: { capabilities: ['isAdmin'] } },
  { to: '/policy-rules', label: 'Policy rules', gate: { capabilities: ['isAdmin'] } },
]

/** Any-of check across roles and capabilities. */
export function allowed(auth, gate) {
  if (!gate) return true
  const byRole = gate.roles?.some((r) => auth.hasRole(r)) ?? false
  const byCap = gate.capabilities?.some((c) => auth.can(c)) ?? false
  return byRole || byCap
}

export function RoleGate({ roles, capabilities, children }) {
  const auth = useAuth()
  if (!allowed(auth, { roles, capabilities })) return <ForbiddenState />
  return children
}

export function RequireAuth({ children }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    const next = `${location.pathname}${location.search}`
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />
  }
  return children
}

export default function AppLayout() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const items = NAV.filter((n) => allowed(auth, n.gate))

  return (
    <div className="app">
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <header className="app-header">
        <span className="brand">
          <span className="brand-mark" aria-hidden="true">
            ₹
          </span>
          ExpenseFlow
        </span>
        <button
          type="button"
          className="btn btn-sm menu-toggle"
          aria-expanded={menuOpen}
          aria-controls="main-nav"
          onClick={() => setMenuOpen((o) => !o)}
        >
          Menu
        </button>
        <nav id="main-nav" aria-label="Main" className={menuOpen ? 'open' : ''}>
          {items.map((n) => (
            <NavLink key={n.to} to={n.to} onClick={() => setMenuOpen(false)}>
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="user-box">
          <span className="user-name">{auth.user?.name}</span>
          <span className="user-role">{ROLE_LABEL[auth.user?.role] ?? auth.user?.role}</span>
          <button
            type="button"
            className="btn btn-sm"
            onClick={() => {
              auth.logout()
              navigate('/login', { replace: true })
            }}
          >
            Sign out
          </button>
        </div>
      </header>
      <main id="main" className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
