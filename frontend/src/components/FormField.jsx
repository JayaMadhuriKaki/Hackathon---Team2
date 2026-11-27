import { cloneElement, useEffect, useId, useRef } from 'react'

/** Label + control + hint + error, wired with id / aria-describedby / aria-invalid (A11Y-01, A11Y-02). */
export function FormField({ label, error, hint, children, required, className = '' }) {
  const autoId = useId()
  const id = children.props.id ?? autoId
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  return (
    <div className={`field ${error ? 'field-invalid' : ''} ${className}`}>
      <label htmlFor={id}>
        {label}
        {required && <span className="req" aria-hidden="true"> *</span>}
      </label>
      {cloneElement(children, {
        id,
        'aria-describedby': describedBy,
        'aria-invalid': error ? 'true' : undefined,
        'aria-required': required ? 'true' : undefined,
      })}
      {hint && (
        <p id={hintId} className="hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  )
}

/** Focusable list of errors; focused when it appears (A11Y-02). */
export function ErrorSummary({ errors, title = 'Please fix the highlighted fields.' }) {
  const ref = useRef(null)
  const entries = Object.entries(errors ?? {}).filter(([, v]) => v)
  const show = entries.length > 0
  useEffect(() => {
    if (show) ref.current?.focus()
  }, [show])
  if (!show) return null
  return (
    <div className="error-summary" role="alert" tabIndex={-1} ref={ref}>
      <p className="state-title">{title}</p>
      <ul>
        {entries.map(([k, v]) => (
          <li key={k}>{v}</li>
        ))}
      </ul>
    </div>
  )
}
