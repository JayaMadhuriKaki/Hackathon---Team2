import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { claimsApi } from '../api/index.js'
import {
  ApprovalSteps,
  AuditHistory,
  AutoRejectBanner,
  ClaimHeader,
  CommentList,
  PolicyResultList,
} from '../components/ClaimParts.jsx'
import { CATEGORY_LABEL, DateDisplay, MoneyDisplay } from '../components/Display.jsx'
import { ReceiptViewer } from '../components/ReceiptViewer.jsx'
import { ErrorState, LoadingState } from '../components/States.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useQuery } from '../hooks/useQuery.js'

const DECIDE = ['APPROVE', 'REJECT', 'SEND_BACK']

/** S-04 ClaimDetailPage (API-006, API-011, API-013): FR-004, FR-018, CL-07. */
export default function ClaimDetailPage() {
  const { claimId } = useParams()
  const claimQ = useQuery((opts) => claimsApi.get(claimId, opts), [claimId])
  const historyQ = useQuery((opts) => claimsApi.auditEvents(claimId, { pageSize: 100 }, opts), [claimId])
  const claim = claimQ.data
  useDocumentTitle(claim ? claim.claimNumber : 'Claim')
  const [receiptLine, setReceiptLine] = useState(null)

  if (claimQ.loading && !claim) return <LoadingState />
  if (claimQ.error && !claim) {
    return (
      <section>
        <h1>Claim</h1>
        <ErrorState error={claimQ.error} onRetry={claimQ.reload} />
        <Link to="/claims">Back to my claims</Link>
      </section>
    )
  }

  const actions = claim.allowedActions ?? []
  const canDecide = actions.some((a) => DECIDE.includes(a))

  return (
    <section className="claim-detail">
      <div className="page-head">
        <h1>{claim.claimNumber}</h1>
        <div className="page-actions">
          {actions.includes('EDIT') && (
            <Link className="btn btn-primary" to={`/claims/${claim.claimId}/edit`}>
              {claim.status === 'SENT_BACK' ? 'Edit and resubmit' : 'Edit claim'}
            </Link>
          )}
          {canDecide && (
            <Link className="btn btn-primary" to={`/approvals/${claim.claimId}`}>
              Review
            </Link>
          )}
        </div>
      </div>

      <ClaimHeader claim={claim} />
      <AutoRejectBanner claim={claim} />
      {claim.status === 'REJECTED' && claim.decisionReason && (
        <div className="banner banner-bad" role="status">
          <p className="state-title">Rejected</p>
          <p>“{claim.decisionReason}”</p>
        </div>
      )}
      {claim.status === 'SENT_BACK' && claim.latestSendBackComment && (
        <div className="banner banner-warn" role="status">
          <p className="state-title">Sent back for clarification</p>
          <p>“{claim.latestSendBackComment}”</p>
        </div>
      )}

      <h2>Lines</h2>
      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Category</th>
              <th scope="col">Date</th>
              <th scope="col">Vendor</th>
              <th scope="col">Receipt no.</th>
              <th scope="col" className="num">
                Amount
              </th>
              <th scope="col">Policy</th>
              <th scope="col">Receipt</th>
            </tr>
          </thead>
          <tbody>
            {claim.lineItems.map((l) => (
              <tr key={l.lineItemId}>
                <td data-label="#">{l.lineNo}</td>
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
                <td data-label="Vendor">{l.vendor}</td>
                <td data-label="Receipt no.">{l.receiptNumber}</td>
                <td data-label="Amount" className="num">
                  <MoneyDisplay value={l.amount} />
                </td>
                <td data-label="Policy">
                  <PolicyResultList results={l.policyResults} />
                </td>
                <td data-label="Receipt">
                  {l.receipt ? (
                    <button type="button" className="link-button" onClick={() => setReceiptLine(l)}>
                      View receipt {l.lineNo}
                    </button>
                  ) : (
                    <span className="muted">None</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {receiptLine && (
        <div className="card receipt-panel">
          <div className="page-head">
            <h2>Receipt — line {receiptLine.lineNo}</h2>
            <button type="button" className="btn btn-sm" onClick={() => setReceiptLine(null)}>
              Close receipt
            </button>
          </div>
          <ReceiptViewer claimId={claim.claimId} line={receiptLine} />
        </div>
      )}

      <div className="two-col">
        <div>
          <h2>Approval steps</h2>
          <ApprovalSteps steps={claim.steps} submissionNo={claim.submissionNo} />
          <h2>Comments</h2>
          <CommentList comments={claim.comments} />
        </div>
        <div>
          <h2>History</h2>
          {historyQ.loading && !historyQ.data ? (
            <LoadingState />
          ) : historyQ.error ? (
            <ErrorState error={historyQ.error} onRetry={historyQ.reload} />
          ) : (
            <AuditHistory events={historyQ.data?.items} />
          )}
        </div>
      </div>
    </section>
  )
}
