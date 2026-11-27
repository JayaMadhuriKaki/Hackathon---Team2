import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { claimsApi } from '../api/index.js'
import { ConfirmDialog } from '../components/ConfirmDialog.jsx'
import { CATEGORY_LABEL, DateDisplay, MoneyDisplay, StatusBadge } from '../components/Display.jsx'
import { LineItemForm } from '../components/LineItemForm.jsx'
import { ErrorState, LoadingState } from '../components/States.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useMutation } from '../hooks/useMutation.js'
import { useQuery } from '../hooks/useQuery.js'
import { fieldErrors, messageFor } from '../utils/errors.js'
import { formatInr, sumMoney } from '../utils/money.js'
import { receiptRequired } from '../utils/validation.js'

/** S-03 ClaimEditorPage (create + edit): FR-001, FR-002, FR-003, CL-05, CL-08, CL-12, ASM-003, ASM-005. */
export default function ClaimEditorPage() {
  const { claimId } = useParams()
  useDocumentTitle(claimId ? 'Edit claim' : 'New claim')
  const navigate = useNavigate()
  const toast = useToast()
  const { config } = useAuth()

  const { data: claim, error, loading, reload } = useQuery(
    (opts) => claimsApi.get(claimId, opts),
    [claimId],
    { enabled: Boolean(claimId) },
  )

  // editing: null (closed) | 'new' | lineItemId
  const [editing, setEditing] = useState(null)
  const [dirty, setDirty] = useState(false)
  const [lineServerErrors, setLineServerErrors] = useState(null)
  const [lineProblems, setLineProblems] = useState({})
  const [submitError, setSubmitError] = useState(null)
  const [confirmSubmit, setConfirmSubmit] = useState(false)
  const [deleteLine, setDeleteLine] = useState(null)
  const [savingLine, setSavingLine] = useState(false)

  // Warn before leaving the page with an unsaved line (SPEC S-03).
  useEffect(() => {
    if (!dirty) return undefined
    const handler = (e) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty])

  const submitMutation = useMutation(useCallback((id, key) => claimsApi.submit(id, key), []))
  const deleteMutation = useMutation(useCallback((cid, lid) => claimsApi.deleteLine(cid, lid), []))

  if (claimId && loading && !claim) return <LoadingState />
  if (claimId && error && !claim) {
    return (
      <section>
        <h1>Edit claim</h1>
        <ErrorState error={error} onRetry={reload} />
      </section>
    )
  }
  // Edit guard (B-04): only DRAFT/SENT_BACK claims the caller may edit.
  if (claim && !claim.allowedActions?.includes('EDIT')) return <Navigate to={`/claims/${claim.claimId}`} replace />

  const lines = claim?.lineItems ?? []
  const total = claim ? sumMoney(lines.map((l) => l.amount)) : '0.00'

  async function ensureClaim() {
    if (claimId) return claimId
    const created = await claimsApi.create()
    return created.claimId
  }

  async function saveLine(body, file) {
    setSavingLine(true)
    setLineServerErrors(null)
    try {
      const id = await ensureClaim()
      const saved =
        editing && editing !== 'new'
          ? await claimsApi.updateLine(id, editing, body)
          : await claimsApi.addLine(id, body)
      if (file) {
        try {
          await claimsApi.uploadReceipt(id, saved.lineItemId, file)
        } catch (err) {
          toast.error(`Line saved, but the receipt upload failed: ${messageFor(err)}`, err.traceId)
        }
      }
      setEditing(null)
      setDirty(false)
      setLineProblems({})
      toast.success(`Line ${saved.lineNo} saved.`)
      if (!claimId) navigate(`/claims/${id}/edit`, { replace: true })
      else reload()
    } catch (err) {
      if (err.code === 'VALIDATION_FAILED') setLineServerErrors(fieldErrors(err))
      else if (err.code === 'STATE_CONFLICT') {
        toast.error(messageFor(err))
        reload()
      } else toast.error(messageFor(err), err.traceId)
    } finally {
      setSavingLine(false)
    }
  }

  async function saveDraft() {
    try {
      if (!claimId) {
        const id = await ensureClaim()
        toast.success('Draft saved.')
        navigate(`/claims/${id}/edit`, { replace: true })
        return
      }
      toast.success('Draft saved. Lines are saved as you add them.')
      navigate('/claims')
    } catch (err) {
      toast.error(messageFor(err), err.traceId)
    }
  }

  function preCheckSubmit() {
    setSubmitError(null)
    if (!lines.length) {
      setSubmitError('Add at least one expense line before submitting.')
      return
    }
    const problems = {}
    for (const l of lines) {
      if (!l.receipt && receiptRequired(l.amount, config?.receiptMandatoryAbove)) {
        problems[l.lineItemId] = `A receipt is required for lines above ${formatInr(config.receiptMandatoryAbove)}.`
      }
    }
    setLineProblems(problems)
    if (Object.keys(problems).length) {
      setSubmitError('Upload a receipt for the highlighted lines before submitting.')
      return
    }
    setConfirmSubmit(true)
  }

  async function doSubmit() {
    try {
      const result = await submitMutation.run(claimId)
      if (!result) return
      setConfirmSubmit(false)
      toast.success(`Claim ${result.claimNumber} submitted.`)
      navigate(`/claims/${claimId}`)
    } catch (err) {
      setConfirmSubmit(false)
      if (err.code === 'RECEIPT_REQUIRED') {
        const problems = {}
        for (const p of err.details?.lines ?? []) {
          problems[p.lineItemId] = `A receipt is required for lines above ${formatInr(config?.receiptMandatoryAbove)}.`
        }
        setLineProblems(problems)
      } else if (err.code === 'STATE_CONFLICT') reload()
      setSubmitError(messageFor(err))
    }
  }

  async function doDelete() {
    try {
      await deleteMutation.run(claimId, deleteLine.lineItemId)
      setDeleteLine(null)
      toast.success('Line deleted.')
      reload()
    } catch (err) {
      setDeleteLine(null)
      toast.error(messageFor(err), err.traceId)
    }
  }

  const editingLine = editing && editing !== 'new' ? lines.find((l) => l.lineItemId === editing) : null

  return (
    <section className="editor">
      <div className="page-head">
        <h1>{claim ? `Edit ${claim.claimNumber}` : 'New claim'}</h1>
        {claim && <StatusBadge status={claim.status} />}
      </div>

      {claim?.status === 'SENT_BACK' && (
        <div className="banner banner-warn" role="status">
          <p className="state-title">This claim was sent back for clarification.</p>
          {claim.latestSendBackComment && <p>“{claim.latestSendBackComment}”</p>}
          <p>Resubmitting restarts approval from the first step.</p>
        </div>
      )}

      <div className={`editor-grid ${editing ? 'editing' : ''}`}>
        <div className="editor-lines">
          <h2>Expense lines</h2>
          {lines.length === 0 ? (
            <p className="muted">No lines yet. Add your first expense.</p>
          ) : (
            <ul className="line-cards">
              {lines.map((l) => (
                <li
                  key={l.lineItemId}
                  className={`line-card ${lineProblems[l.lineItemId] ? 'line-invalid' : ''} ${
                    editing === l.lineItemId ? 'line-active' : ''
                  }`}
                >
                  <div className="line-card-head">
                    <strong>
                      {l.lineNo}. {CATEGORY_LABEL[l.category] ?? l.category}
                    </strong>
                    <MoneyDisplay value={l.amount} />
                  </div>
                  <div className="line-card-meta">
                    {l.vendor} · <DateDisplay value={l.expenseDate} dateOnly /> · Receipt no. {l.receiptNumber}
                    {l.category === 'HOTEL' && (
                      <>
                        {' '}
                        · {l.city}, {l.nights} night(s)
                      </>
                    )}
                  </div>
                  <div className="line-card-meta">{l.receipt ? '📎 Receipt attached' : 'No receipt'}</div>
                  {lineProblems[l.lineItemId] && <p className="field-error">{lineProblems[l.lineItemId]}</p>}
                  <div className="line-card-actions">
                    <button
                      type="button"
                      className="btn btn-sm"
                      onClick={() => setEditing(l.lineItemId)}
                      disabled={Boolean(editing)}
                    >
                      Edit line {l.lineNo}
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-danger-ghost"
                      onClick={() => setDeleteLine(l)}
                      disabled={Boolean(editing)}
                    >
                      Delete line {l.lineNo}
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {!editing && (
            <button type="button" className="btn btn-block add-line" onClick={() => setEditing('new')}>
              + Add line
            </button>
          )}
        </div>

        <div className={`editor-form ${editing ? 'open' : ''}`}>
          {editing ? (
            <>
              <h2>{editingLine ? `Edit line ${editingLine.lineNo}` : 'New line'}</h2>
              <LineItemForm
                key={editing}
                initial={editingLine}
                existingReceipt={Boolean(editingLine?.receipt)}
                serverErrors={lineServerErrors}
                pending={savingLine}
                onSave={saveLine}
                onCancel={() => {
                  setEditing(null)
                  setDirty(false)
                  setLineServerErrors(null)
                }}
                onDirtyChange={setDirty}
              />
            </>
          ) : (
            <p className="muted desktop-only">Select “Add line” or edit an existing line.</p>
          )}
        </div>
      </div>

      {submitError && (
        <p className="banner banner-bad" role="alert">
          {submitError}
        </p>
      )}

      <div className="sticky-bar">
        <span className="sticky-total">
          Total <MoneyDisplay value={total} />
        </span>
        <Link to="/claims" className="btn">
          Back
        </Link>
        <button type="button" className="btn" onClick={saveDraft} disabled={Boolean(editing) || savingLine}>
          Save draft
        </button>
        <button
          type="button"
          className="btn btn-primary"
          onClick={preCheckSubmit}
          disabled={!claimId || Boolean(editing) || submitMutation.pending}
        >
          Submit
        </button>
      </div>

      <ConfirmDialog
        open={confirmSubmit}
        title="Submit claim?"
        confirmLabel="Submit claim"
        pending={submitMutation.pending}
        onConfirm={doSubmit}
        onCancel={() => setConfirmSubmit(false)}
      >
        <p>
          Submit claim of <MoneyDisplay value={total} /> with {lines.length} line(s)? Lines cannot be edited after
          submit.
        </p>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(deleteLine)}
        title="Delete line?"
        confirmLabel="Delete line"
        tone="danger"
        pending={deleteMutation.pending}
        onConfirm={doDelete}
        onCancel={() => setDeleteLine(null)}
      >
        {deleteLine && (
          <p>
            Delete line {deleteLine.lineNo} ({deleteLine.vendor}, <MoneyDisplay value={deleteLine.amount} />)?
          </p>
        )}
      </ConfirmDialog>
    </section>
  )
}
