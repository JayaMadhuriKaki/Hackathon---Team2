import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { approvalsApi, claimsApi } from '../api/index.js'
import {
  ApprovalSteps,
  AuditHistory,
  AutoRejectBanner,
  ClaimHeader,
  CommentList,
  PolicyResultList,
} from '../components/ClaimParts.jsx'
import { ConfirmDialog } from '../components/ConfirmDialog.jsx'
import { CATEGORY_LABEL, DateDisplay, MoneyDisplay } from '../components/Display.jsx'
import { ReceiptViewer } from '../components/ReceiptViewer.jsx'
import { ErrorState, LoadingState } from '../components/States.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useMutation } from '../hooks/useMutation.js'
import { useQuery } from '../hooks/useQuery.js'
import { messageFor } from '../utils/errors.js'

const ACTIONS = {
  APPROVE: {
    title: 'Approve claim?',
    label: 'Approve',
    tone: 'primary',
    comment: 'optional',
    call: approvalsApi.approve,
    done: 'approved',
  },
  REJECT: {
    title: 'Reject claim?',
    label: 'Reject',
    tone: 'danger',
    comment: 'required',
    call: approvalsApi.reject,
    done: 'rejected',
  },
  SEND_BACK: {
    title: 'Send back for clarification?',
    label: 'Send back',
    tone: 'warn',
    comment: 'required',
    call: approvalsApi.sendBack,
    done: 'sent back',
  },
}

/**
 * S-06 ApproverReviewPage — claim and receipt side by side (NFR-002).
 * Actions are rendered only when present in allowedActions (AC-09). FR-005..007, FR-020, CL-04, CL-14.
 */
export default function ApproverReviewPage({ mode = 'approvals' }) {
  const { claimId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { user } = useAuth()
  const claimQ = useQuery((opts) => claimsApi.get(claimId, opts), [claimId])
  const historyQ = useQuery((opts) => claimsApi.auditEvents(claimId, { pageSize: 100 }, opts), [claimId])
  const claim = claimQ.data
  useDocumentTitle(claim ? `Review ${claim.claimNumber}` : 'Review claim')

  const [selectedIdx, setSelectedIdx] = useState(0)
  const [dialog, setDialog] = useState(null)
  const [dialogError, setDialogError] = useState(null)
  const [comment, setComment] = useState('')
  const [commentPending, setCommentPending] = useState(false)

  const backTo = mode === 'review' ? '/review-queue' : '/approvals'

  const decide = useMutation(
    useCallback((action, id, text, key) => ACTIONS[action].call(id, text, key), []),
  )

  const lines = claim?.lineItems ?? []
  const selected = lines[Math.min(selectedIdx, lines.length - 1)]

  if (claimQ.loading && !claim) return <LoadingState />
  if (claimQ.error && !claim) {
    return (
      <section>
        <h1>Review claim</h1>
        <ErrorState error={claimQ.error} onRetry={claimQ.reload} />
        <Link to={backTo}>Back to queue</Link>
      </section>
    )
  }

  const actions = claim.allowedActions ?? []
  const ownClaim = claim.claimant?.userId === user?.userId
  const pendingStep = claim.steps?.find((s) => s.status === 'PENDING' && s.submissionNo === claim.submissionNo)
  const delegatedFor =
    pendingStep?.assignedApprover && pendingStep.assignedApprover.userId !== user?.userId
      ? pendingStep.assignedApprover
      : null

  async function confirm(text) {
    setDialogError(null)
    try {
      const result = await decide.run(dialog, claim.claimId, text)
      if (!result) return
      toast.success(`${claim.claimNumber} ${ACTIONS[dialog].done}.`)
      setDialog(null)
      navigate(backTo)
    } catch (err) {
      if (err.code === 'COMMENT_REQUIRED') {
        setDialogError(messageFor(err))
        return
      }
      setDialog(null)
      toast.error(messageFor(err), err.traceId)
      if (['STATE_CONFLICT', 'NOT_CURRENT_APPROVER'].includes(err.code)) {
        claimQ.reload()
        historyQ.reload()
      }
    }
  }

  async function addComment(e) {
    e.preventDefault()
    const body = comment.trim()
    if (!body || commentPending) return
    setCommentPending(true)
    try {
      await approvalsApi.comment(claim.claimId, body)
      setComment('')
      toast.success('Comment added.')
      claimQ.reload()
      historyQ.reload()
    } catch (err) {
      toast.error(messageFor(err), err.traceId)
    } finally {
      setCommentPending(false)
    }
  }

  function onLinesKey(e) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setSelectedIdx((i) => Math.min(lines.length - 1, i + 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setSelectedIdx((i) => Math.max(0, i - 1))
    }
  }

  const decisionButtons = ['APPROVE', 'SEND_BACK', 'REJECT'].filter((a) => actions.includes(a))

  return (
    <section className="review">
      <div className="page-head">
        <h1>Review {claim.claimNumber}</h1>
        <Link to={backTo}>Back to queue</Link>
      </div>

      {ownClaim && (
        <p className="banner banner-warn" role="status">
          You cannot act on your own claim.
        </p>
      )}
      {delegatedFor && decisionButtons.length > 0 && (
        <p className="banner banner-info" role="status">
          Acting on behalf of {delegatedFor.name}.
        </p>
      )}
      {claim.status === 'SAMPLE_AUDIT_PENDING' && (
        <p className="banner banner-info" role="status">
          This auto-approved claim was selected for the 10% sample audit. It is held from payout until you approve or
          reject it.
        </p>
      )}

      <div className="split">
        <div className="split-main">
          <ClaimHeader claim={claim} />
          <AutoRejectBanner claim={claim} />

          <h2 id="lines-heading">Lines</h2>
          <p className="hint">Select a line (click, or ↑/↓) to show its receipt.</p>
          <div className="table-wrap">
            <table className="data-table lines-table" aria-labelledby="lines-heading">
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">Category</th>
                  <th scope="col">Date</th>
                  <th scope="col">Vendor</th>
                  <th scope="col" className="num">
                    Amount
                  </th>
                  <th scope="col">Policy</th>
                </tr>
              </thead>
              <tbody onKeyDown={onLinesKey}>
                {lines.map((l, i) => (
                  <tr
                    key={l.lineItemId}
                    className={`clickable ${selected?.lineItemId === l.lineItemId ? 'selected' : ''}`}
                    onClick={() => setSelectedIdx(i)}
                    aria-selected={selected?.lineItemId === l.lineItemId}
                  >
                    <td data-label="#">
                      <button type="button" className="link-button row-link" onClick={() => setSelectedIdx(i)}>
                        {l.lineNo}
                      </button>
                    </td>
                    <td data-label="Category">
                      {CATEGORY_LABEL[l.category] ?? l.category}
                      {l.category === 'HOTEL' && (
                        <div className="muted">
                          {l.city}, {l.nights} night(s)
                        </div>
                      )}
                    </td>
                    <td data-label="Date">
                      <DateDisplay value={l.expenseDate} dateOnly />
                    </td>
                    <td data-label="Vendor">
                      {l.vendor}
                      <div className="muted">No. {l.receiptNumber}</div>
                    </td>
                    <td data-label="Amount" className="num">
                      <MoneyDisplay value={l.amount} />
                    </td>
                    <td data-label="Policy">
                      <PolicyResultList results={l.policyResults} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h2>Approval steps</h2>
          <ApprovalSteps steps={claim.steps} submissionNo={claim.submissionNo} />

          <h2>Comments</h2>
          <CommentList comments={claim.comments} />
          {actions.includes('COMMENT') && (
            <form className="comment-form" onSubmit={addComment}>
              <label htmlFor="new-comment">Add a comment</label>
              <textarea
                id="new-comment"
                rows={2}
                maxLength={2000}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
              <button type="submit" className="btn" disabled={!comment.trim() || commentPending}>
                {commentPending ? 'Posting…' : 'Post comment'}
              </button>
            </form>
          )}

          <h2>History</h2>
          {historyQ.error ? (
            <ErrorState error={historyQ.error} onRetry={historyQ.reload} />
          ) : (
            <AuditHistory events={historyQ.data?.items} />
          )}
        </div>

        <aside className="split-receipt" aria-label="Receipt">
          <h2 className="sr-only">Receipt</h2>
          <ReceiptViewer claimId={claim.claimId} line={selected} />
        </aside>
      </div>

      {decisionButtons.length > 0 && (
        <div className="sticky-bar">
          <span className="sticky-total">
            Total <MoneyDisplay value={claim.total} />
          </span>
          {decisionButtons.map((a) => (
            <button
              key={a}
              type="button"
              className={`btn btn-${ACTIONS[a].tone}`}
              onClick={() => {
                setDialogError(null)
                setDialog(a)
              }}
              disabled={decide.pending}
            >
              {ACTIONS[a].label}
            </button>
          ))}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(dialog)}
        title={dialog ? ACTIONS[dialog].title : ''}
        confirmLabel={dialog ? ACTIONS[dialog].label : ''}
        tone={dialog ? ACTIONS[dialog].tone : 'primary'}
        commentMode={dialog ? ACTIONS[dialog].comment : 'none'}
        commentLabel={dialog === 'APPROVE' ? 'Comment' : 'Reason'}
        pending={decide.pending}
        error={dialogError}
        onConfirm={confirm}
        onCancel={() => setDialog(null)}
      >
        <p>
          {claim.claimNumber} · {claim.claimant?.name} · <MoneyDisplay value={claim.total} />
        </p>
        {dialog === 'REJECT' && <p>Rejection ends the claim. A reason is required.</p>}
        {dialog === 'SEND_BACK' && (
          <p>The claimant can edit and resubmit. Approval will restart from the first step.</p>
        )}
      </ConfirmDialog>
    </section>
  )
}
