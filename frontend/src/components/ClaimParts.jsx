// Read-only claim building blocks shared by S-04 and S-06.
import { CATEGORY_LABEL, DateDisplay, MoneyDisplay, STEP_LABEL, StatusBadge, statusLabel } from './Display.jsx'

const RESULT_TONE = {
  LIMIT_BREACH: 'bad',
  DUPLICATE_FLAG: 'warn',
  WEEKEND_FLAG: 'warn',
  VALIDATION: 'bad',
}

/** Policy results for one line: rule ID, reason, limit vs actual (FR-004). Passing rules are hidden. */
export function PolicyResultList({ results }) {
  const shown = (results ?? []).filter((r) => r.resultType && r.resultType !== 'PASS')
  if (!shown.length) {
    return <span className="policy-pass">✓ Passes policy</span>
  }
  return (
    <ul className="policy-results">
      {shown.map((r, i) => (
        <li key={`${r.ruleId}-${i}`} className={`policy policy-${RESULT_TONE[r.resultType] ?? 'warn'}`}>
          <strong>{r.ruleId}</strong> {r.reason}
          {(r.configuredLimit || r.actualValue) && (
            <span className="policy-numbers">
              {r.configuredLimit && <> Limit {fmtValue(r.configuredLimit)}</>}
              {r.actualValue && <> · Actual {fmtValue(r.actualValue)}</>}
            </span>
          )}
        </li>
      ))}
    </ul>
  )
}

function fmtValue(v) {
  return /^\d+(\.\d{1,2})?$/.test(String(v)) ? <MoneyDisplay value={v} /> : String(v)
}

/** Every breaching line for an AUTO_REJECTED claim (CL-07). */
export function AutoRejectBanner({ claim }) {
  if (claim.status !== 'AUTO_REJECTED') return null
  const breaches = claim.lineItems.flatMap((l) =>
    (l.policyResults ?? []).filter((r) => r.resultType === 'LIMIT_BREACH').map((r) => ({ line: l, r })),
  )
  return (
    <div className="banner banner-bad" role="alert">
      <p className="state-title">This claim was automatically rejected because it breaks policy limits.</p>
      {breaches.length > 0 ? (
        <ul>
          {breaches.map(({ line, r }, i) => (
            <li key={i}>
              Line {line.lineNo} ({CATEGORY_LABEL[line.category] ?? line.category}): {r.ruleId} — {r.reason}
              {r.configuredLimit && (
                <>
                  {' '}
                  (limit <MoneyDisplay value={r.configuredLimit} />, actual <MoneyDisplay value={r.actualValue} />)
                </>
              )}
            </li>
          ))}
        </ul>
      ) : (
        claim.decisionReason && <p>{claim.decisionReason}</p>
      )}
    </div>
  )
}

/** Header block for a claim. */
export function ClaimHeader({ claim }) {
  return (
    <dl className="claim-header">
      <div>
        <dt>Status</dt>
        <dd>
          <StatusBadge status={claim.status} />
        </dd>
      </div>
      <div>
        <dt>Total</dt>
        <dd>
          <MoneyDisplay value={claim.total} className="money-lg" />
        </dd>
      </div>
      <div>
        <dt>Claimant</dt>
        <dd>
          {claim.claimant?.name} <span className="muted">({claim.claimant?.employeeCode})</span>
        </dd>
      </div>
      <div>
        <dt>Department</dt>
        <dd>{claim.department ?? '—'}</dd>
      </div>
      <div>
        <dt>Submitted</dt>
        <dd>
          <DateDisplay value={claim.submittedAt} />
        </dd>
      </div>
    </dl>
  )
}

/** Approval steps of the current submission; earlier submissions collapsed (FR-005, CL-12). */
export function ApprovalSteps({ steps, submissionNo }) {
  if (!steps?.length) return <p className="muted">No approval steps (not routed to the approval chain).</p>
  const current = steps.filter((s) => s.submissionNo === submissionNo && s.status !== 'SUPERSEDED')
  const earlier = steps.filter((s) => s.submissionNo !== submissionNo || s.status === 'SUPERSEDED')
  return (
    <>
      <ol className="steps">
        {current.map((s) => (
          <StepItem key={s.stepId} step={s} />
        ))}
      </ol>
      {earlier.length > 0 && (
        <details className="earlier">
          <summary>Earlier submission ({earlier.length} steps)</summary>
          <ol className="steps">
            {earlier.map((s) => (
              <StepItem key={s.stepId} step={s} />
            ))}
          </ol>
        </details>
      )}
    </>
  )
}

function StepItem({ step }) {
  return (
    <li className={`step step-${step.status.toLowerCase()}`}>
      <div className="step-head">
        <strong>
          {step.level}. {STEP_LABEL[step.stepType] ?? step.stepType}
        </strong>
        <StatusBadge status={step.status} />
      </div>
      <div className="step-meta">
        {step.assignedApprover ? (
          <span>Approver: {step.assignedApprover.name}</span>
        ) : (
          <span>Approver: any {STEP_LABEL[step.stepType]?.toLowerCase() ?? 'approver'}</span>
        )}
        {step.skipReason && <span> · Skipped: {step.skipReason.replaceAll('_', ' ').toLowerCase()}</span>}
        {step.actedBy && (
          <span>
            {' '}
            · {statusLabel(step.status)} by {step.actedBy.name}
            {step.onBehalfOf && <> on behalf of {step.onBehalfOf.name}</>} <DateDisplay value={step.actedAt} />
          </span>
        )}
      </div>
    </li>
  )
}

export function CommentList({ comments }) {
  if (!comments?.length) return <p className="muted">No comments yet.</p>
  return (
    <ul className="comments">
      {comments.map((c) => (
        <li key={c.commentId}>
          <div className="comment-head">
            <strong>{c.author?.name}</strong> <DateDisplay value={c.createdAt} />
          </div>
          <p className="comment-body">{c.body}</p>
        </li>
      ))}
    </ul>
  )
}

/** Audit timeline from API-013 (FR-018). Read-only. */
export function AuditHistory({ events }) {
  if (!events?.length) return <p className="muted">No history yet.</p>
  return (
    <ol className="timeline">
      {events.map((e) => (
        <li key={e.eventId}>
          <DateDisplay value={e.occurredAt} /> — <strong>{e.action.replaceAll('_', ' ').toLowerCase()}</strong>
          {e.actor && <> by {e.actor.name}</>}
          {e.onBehalfOf && <> on behalf of {e.onBehalfOf.name}</>}
          {e.fromState && e.toState && e.fromState !== e.toState && (
            <span className="muted">
              {' '}
              ({statusLabel(e.fromState)} → {statusLabel(e.toState)})
            </span>
          )}
          {e.comment && <p className="comment-body">“{e.comment}”</p>}
        </li>
      ))}
    </ol>
  )
}
