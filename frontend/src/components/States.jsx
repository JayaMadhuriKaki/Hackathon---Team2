import { messageFor } from '../utils/errors.js'

export function LoadingState({ label = 'Loading…' }) {
  return (
    <div className="state state-loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" /> {label}
    </div>
  )
}

export function EmptyState({ title, children }) {
  return (
    <div className="state state-empty">
      <p className="state-title">{title}</p>
      {children}
    </div>
  )
}

/** Human message + optional retry + "Reference: traceId". No raw bodies (AC-16). */
export function ErrorState({ error, onRetry, message }) {
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">{message ?? messageFor(error)}</p>
      {error?.traceId && <p className="trace">Reference: {error.traceId}</p>}
      {onRetry && (
        <button type="button" className="btn" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function ForbiddenState() {
  return (
    <div className="state state-error" role="alert">
      <h1>Not available</h1>
      <p>You do not have permission to view this page.</p>
    </div>
  )
}
