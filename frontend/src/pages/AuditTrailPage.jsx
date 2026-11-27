import { useState } from 'react'
import { auditApi } from '../api/index.js'
import { DataTable } from '../components/DataTable.jsx'
import { DateDisplay, statusLabel } from '../components/Display.jsx'
import { FormField } from '../components/FormField.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useQuery } from '../hooks/useQuery.js'
import { useUrlQuery } from '../hooks/useUrlQuery.js'

const ACTIONS = [
  'CLAIM_CREATED', 'LINE_ADDED', 'LINE_UPDATED', 'LINE_DELETED', 'RECEIPT_UPLOADED', 'CLAIM_SUBMITTED',
  'POLICY_EVALUATED', 'CLAIM_AUTO_REJECTED', 'CLAIM_ROUTED', 'CLAIM_AUTO_APPROVED', 'SAMPLE_AUDIT_SELECTED',
  'SAMPLE_AUDIT_NOT_SELECTED', 'STEP_ACTIVATED', 'STEP_SKIPPED', 'STEP_APPROVED', 'CLAIM_APPROVED', 'CLAIM_REJECTED',
  'CLAIM_SENT_BACK', 'COMMENT_ADDED', 'DELEGATION_CREATED', 'DELEGATION_CANCELLED', 'PAYOUT_BATCH_CREATED',
  'CLAIM_ADDED_TO_BATCH', 'PAYOUT_BATCH_EXPORTED', 'CLAIM_PAID', 'POLICY_RULE_CHANGED', 'LOGIN_SUCCEEDED',
  'LOGIN_FAILED',
]

const FILTERS = ['claimNumber', 'action', 'fromDate', 'toDate']

/** Renders `details` as escaped key/value text — never as HTML. */
function DetailsView({ details }) {
  if (details === null || details === undefined) return <span className="muted">—</span>
  if (typeof details !== 'object') return <span>{String(details)}</span>
  return (
    <dl className="kv">
      {Object.entries(details).map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</dd>
        </div>
      ))}
    </dl>
  )
}

/** S-12 AuditTrailPage (API-033): FR-018, NFR-004. Read-only — no edit or delete controls. */
export default function AuditTrailPage() {
  useDocumentTitle('Audit trail')
  const [q, setQ] = useUrlQuery({ page: 1, pageSize: 50 })
  const [draft, setDraft] = useState(() => Object.fromEntries(FILTERS.map((k) => [k, q[k] ?? ''])))
  const [expanded, setExpanded] = useState(new Set())
  const query = { page: q.page, pageSize: q.pageSize, ...Object.fromEntries(FILTERS.map((k) => [k, q[k]])) }
  const { data, error, loading, reload } = useQuery((opts) => auditApi.search(query, opts), [JSON.stringify(query)])

  function toggle(id) {
    const next = new Set(expanded)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setExpanded(next)
  }

  const columns = [
    { key: 'occurredAt', header: 'Time (IST)', render: (e) => <DateDisplay value={e.occurredAt} /> },
    { key: 'actor', header: 'Actor', render: (e) => e.actor?.name ?? <span className="muted">system</span> },
    { key: 'onBehalfOf', header: 'On behalf of', render: (e) => e.onBehalfOf?.name ?? '' },
    { key: 'action', header: 'Action', render: (e) => <code>{e.action}</code> },
    { key: 'entity', header: 'Entity', render: (e) => e.entityType },
    { key: 'claimNumber', header: 'Claim', render: (e) => e.claimNumber ?? '' },
    {
      key: 'states',
      header: 'From → To',
      render: (e) => (e.fromState || e.toState ? `${statusLabel(e.fromState) || '—'} → ${statusLabel(e.toState) || '—'}` : ''),
    },
    { key: 'comment', header: 'Comment', render: (e) => e.comment ?? '' },
    {
      key: 'details',
      header: 'Details',
      render: (e) => (
        <>
          <button
            type="button"
            className="link-button"
            aria-expanded={expanded.has(e.eventId)}
            onClick={() => toggle(e.eventId)}
          >
            {expanded.has(e.eventId) ? 'Hide' : 'Show'}
          </button>
          {expanded.has(e.eventId) && <DetailsView details={e.details} />}
        </>
      ),
    },
  ]

  return (
    <section>
      <h1>Audit trail</h1>
      <p className="hint">Append-only record of every action. Entries cannot be edited or deleted.</p>
      <form
        className="filter-bar"
        aria-label="Audit filters"
        onSubmit={(e) => {
          e.preventDefault()
          setQ(draft)
        }}
      >
        <FormField label="Claim number">
          <input
            type="text"
            placeholder="CLM-2026-00001"
            value={draft.claimNumber}
            onChange={(e) => setDraft({ ...draft, claimNumber: e.target.value })}
          />
        </FormField>
        <FormField label="Action">
          <select value={draft.action} onChange={(e) => setDraft({ ...draft, action: e.target.value })}>
            <option value="">All</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="From">
          <input type="date" value={draft.fromDate} onChange={(e) => setDraft({ ...draft, fromDate: e.target.value })} />
        </FormField>
        <FormField label="To">
          <input type="date" value={draft.toDate} onChange={(e) => setDraft({ ...draft, toDate: e.target.value })} />
        </FormField>
        <div className="filter-actions">
          <button type="submit" className="btn btn-primary">
            Apply
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => {
              const empty = Object.fromEntries(FILTERS.map((k) => [k, '']))
              setDraft(empty)
              setQ(empty)
            }}
          >
            Clear
          </button>
        </div>
      </form>
      <DataTable
        caption="Audit events, newest first"
        columns={columns}
        rows={data?.items}
        getRowId={(e) => e.eventId}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyTitle="No audit events match these filters."
        page={data?.page}
        pageSize={data?.pageSize}
        total={data?.total}
        onPageChange={(page) => setQ({ page }, { resetPage: false })}
      />
    </section>
  )
}
