import { useEffect, useId, useRef, useState } from 'react'

/**
 * Modal confirm with optional/required comment (SPEC §5).
 * Focus trapped, Esc closes, focus restored; Confirm disabled while a required comment
 * is empty or while the request is in flight (AC-07, AC-05).
 */
export function ConfirmDialog({ open, ...props }) {
  // Mounted only while open, so the comment state starts empty every time.
  if (!open) return null
  return <DialogBody {...props} />
}

function DialogBody({
  title,
  children,
  confirmLabel = 'Confirm',
  tone = 'primary',
  commentMode = 'none',
  commentLabel = 'Comment',
  pending = false,
  error,
  onConfirm,
  onCancel,
}) {
  const [comment, setComment] = useState('')
  const dialogRef = useRef(null)
  const restoreRef = useRef(null)
  const titleId = useId()
  const commentId = useId()

  useEffect(() => {
    restoreRef.current = document.activeElement
    const t = setTimeout(() => {
      const first = dialogRef.current?.querySelector('textarea, button:not([disabled])')
      first?.focus()
    }, 0)
    return () => {
      clearTimeout(t)
      restoreRef.current?.focus?.()
    }
  }, [])

  const required = commentMode === 'required'
  const blocked = pending || (required && comment.trim() === '')

  function onKeyDown(e) {
    if (e.key === 'Escape' && !pending) {
      e.stopPropagation()
      onCancel()
    }
    if (e.key === 'Tab') {
      const nodes = dialogRef.current.querySelectorAll('button:not([disabled]), textarea, input, select')
      if (!nodes.length) return
      const first = nodes[0]
      const last = nodes[nodes.length - 1]
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
  }

  return (
    <div className="dialog-backdrop">
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        ref={dialogRef}
        onKeyDown={onKeyDown}
      >
        <h2 id={titleId}>{title}</h2>
        <div className="dialog-body">{children}</div>
        {commentMode !== 'none' && (
          <div className="field">
            <label htmlFor={commentId}>
              {commentLabel}
              {required ? <span className="req"> (required)</span> : <span className="muted"> (optional)</span>}
            </label>
            <textarea
              id={commentId}
              rows={3}
              maxLength={2000}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              aria-required={required ? 'true' : undefined}
            />
          </div>
        )}
        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}
        <div className="dialog-actions">
          <button type="button" className="btn" onClick={onCancel} disabled={pending}>
            Cancel
          </button>
          <button
            type="button"
            className={`btn btn-${tone}`}
            disabled={blocked}
            onClick={() => onConfirm(comment.trim())}
          >
            {pending ? 'Working…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
