import { useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { FormField } from '../components/FormField.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'

/** Only same-origin relative paths are accepted for ?next= (no open redirect, §13.3). */
export function safeNext(next) {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return '/claims'
  if (next.startsWith('/login')) return '/claims'
  return next
}

/** S-01 LoginPage (API-002, API-003). */
export default function LoginPage() {
  useDocumentTitle('Sign in')
  const { login, isAuthenticated } = useAuth()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const next = safeNext(params.get('next'))
  const expired = params.get('expired') === '1'

  if (isAuthenticated) return <Navigate to={next} replace />

  async function onSubmit(e) {
    e.preventDefault()
    if (pending) return
    const fe = {}
    if (!email.trim()) fe.email = 'Enter your email.'
    if (!password) fe.password = 'Enter your password.'
    setFieldErrors(fe)
    if (Object.keys(fe).length) return
    setPending(true)
    setError(null)
    try {
      await login(email.trim(), password)
      navigate(next, { replace: true })
    } catch (err) {
      if (err.status === 401 || err.status === 400) setError('Email or password is incorrect.')
      else if (err.status === 429) setError('Too many attempts. Please wait a moment and try again.')
      else setError('Something went wrong. Please try again.')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="login-wrap">
      <main id="main" className="login-card card">
        <p className="brand login-brand">
          <span className="brand-mark" aria-hidden="true">
            ₹
          </span>
          ExpenseFlow
        </p>
        <h1>Sign in</h1>
        {expired && !error && (
          <p className="banner banner-warn" role="status">
            Your session has ended. Please sign in again.
          </p>
        )}
        <form onSubmit={onSubmit} noValidate>
          <FormField label="Email" error={fieldErrors.email} required>
            <input
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </FormField>
          <FormField label="Password" error={fieldErrors.password} required>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </FormField>
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn btn-primary btn-block" disabled={pending}>
            {pending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </main>
    </div>
  )
}
