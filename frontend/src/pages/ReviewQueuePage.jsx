import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { departmentsApi, reviewQueueApi } from '../api/index.js'
import { ConfirmDialog } from '../components/ConfirmDialog.jsx'
import { BulkActionBar, DataTable } from '../components/DataTable.jsx'
import { CATEGORY_LABEL, DateDisplay, FlagList, MoneyDisplay, StatusBadge } from '../components/Display.jsx'
import { FormField } from '../components/FormField.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useMutation } from '../hooks/useMutation.js'
import { useQuery } from '../hooks/useQuery.js'
import { useUrlQuery } from '../hooks/useUrlQuery.js'
import { ageFrom } from '../utils/dates.js'
import { messageFor } from '../utils/errors.js'
import { sumMoney } from '../utils/money.js'
import { validateQueueFilters } from '../utils/validation.js'

const TABS = [
  { value: '', label: 'All' },
  { value: 'AUDITOR_STEP', label: 'Auditor step' },
  { value: 'SAMPLE_AUDIT', label: 'Sample audit' },
]

const FILTER_KEYS = ['departmentId', 'category', 'minAmount', 'maxAmount', 'fromDate', 'toDate', 'flag']

/** S-07 ReviewQueuePage (API-022, API-023): filters + bulk approve. FR-009, FR-010, FR-011, CL-14. */
export default function ReviewQueuePage() {
  useDocumentTitle('Review queue')
  const navigate = useNavigate()
  const toast = useToast()
  const { config } = useAuth()
  const [q, setQ] = useUrlQuery({ page: 1, pageSize: 20 })
  const [draft, setDraft] = useState(() => Object.fromEntries(FILTER_KEYS.map((k) => [k, q[k] ?? ''])))
  const [filterErrors, setFilterErrors] = useState({})
  const [selected, setSelected] = useState(new Set())
  const [rowMessages, setRowMessages] = useState({})
  const [confirmOpen, setConfirmOpen] = useState(false)

  const apiQuery = useMemo(
    () => ({
      page: q.page,
      pageSize: q.pageSize,
      queue: q.queue,
      ...Object.fromEntries(FILTER_KEYS.map((k) => [k, q[k]])),
    }),
    [q],
  )
  const { data, error, loading, reload } = useQuery((opts) => reviewQueueApi.list(apiQuery, opts), [
    JSON.stringify(apiQuery),
  ])
  const deptQ = useQuery((opts) => departmentsApi.list(opts), [])

  const bulk = useMutation(useCallback((ids, key) => reviewQueueApi.bulkApprove(ids, key), []))

  const rows = data?.items
  const selectedRows = (rows ?? []).filter((r) => selected.has(r.claimId))
  const selectedTotal = sumMoney(selectedRows.map((r) => r.total))
  const flaggedCount = selectedRows.filter((r) => r.flags?.length).length

  function applyFilters(e) {
    e.preventDefault()
    const errs = validateQueueFilters(draft)
    setFilterErrors(errs)
    if (Object.keys(errs).length) return
    setSelected(new Set())
    setRowMessages({})
    setQ(draft)
  }

  function clearFilters() {
    const empty = Object.fromEntries(FILTER_KEYS.map((k) => [k, '']))
    setDraft(empty)
    setFilterErrors({})
    setSelected(new Set())
    setQ(empty)
  }

  async function doBulk() {
    const ids = [...selected]
    try {
      const result = await bulk.run(ids)
      if (!result) return
      setConfirmOpen(false)
      const failed = result.results.filter((r) => r.outcome !== 'APPROVED')
      const messages = {}
      for (const f of failed) messages[f.claimId] = messageFor({ code: f.code, message: f.message })
      setRowMessages(messages)
      setSelected(new Set(failed.map((f) => f.claimId)))
      const text = `${result.approved} approved, ${result.failed} failed.`
      if (result.failed) toast.error(text)
      else toast.success(text)
      reload()
    } catch (err) {
      setConfirmOpen(false)
      toast.error(messageFor(err), err.traceId)
    }
  }

  const columns = [
    { key: 'claimNumber', header: 'Claim' },
    { key: 'claimant', header: 'Claimant', render: (r) => r.claimant?.name },
    { key: 'department', header: 'Department' },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
    {
      key: 'categories',
      header: 'Categories',
      render: (r) => (r.categories ?? []).map((c) => CATEGORY_LABEL[c] ?? c).join(', '),
    },
    { key: 'total', header: 'Total', align: 'right', render: (r) => <MoneyDisplay value={r.total} /> },
    { key: 'flags', header: 'Flags', render: (r) => <FlagList flags={r.flags} /> },
    { key: 'submittedAt', header: 'Submitted', render: (r) => <DateDisplay value={r.submittedAt} /> },
    { key: 'waitingSince', header: 'Waiting', render: (r) => ageFrom(r.waitingSince) },
  ]

  const tab = q.queue ?? ''

  return (
    <section>
      <div className="page-head">
        <h1>Review queue</h1>
        {data && <span className="muted">{data.total} claims</span>}
      </div>

      <div className="tabs" role="tablist" aria-label="Queue">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            className={`tab ${tab === t.value ? 'active' : ''}`}
            onClick={() => {
              setSelected(new Set())
              setRowMessages({})
              setQ({ queue: t.value })
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      <form className="filter-bar" onSubmit={applyFilters} aria-label="Filters">
        <FormField label="Department">
          <select value={draft.departmentId} onChange={(e) => setDraft({ ...draft, departmentId: e.target.value })}>
            <option value="">All</option>
            {(deptQ.data ?? []).map((d) => (
              <option key={d.departmentId} value={d.departmentId}>
                {d.name}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Category">
          <select value={draft.category} onChange={(e) => setDraft({ ...draft, category: e.target.value })}>
            <option value="">All</option>
            {(config?.categories ?? []).map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABEL[c] ?? c}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Min ₹" error={filterErrors.minAmount}>
          <input
            type="text"
            inputMode="decimal"
            value={draft.minAmount}
            onChange={(e) => setDraft({ ...draft, minAmount: e.target.value })}
          />
        </FormField>
        <FormField label="Max ₹" error={filterErrors.maxAmount}>
          <input
            type="text"
            inputMode="decimal"
            value={draft.maxAmount}
            onChange={(e) => setDraft({ ...draft, maxAmount: e.target.value })}
          />
        </FormField>
        <FormField label="From">
          <input type="date" value={draft.fromDate} onChange={(e) => setDraft({ ...draft, fromDate: e.target.value })} />
        </FormField>
        <FormField label="To" error={filterErrors.toDate}>
          <input type="date" value={draft.toDate} onChange={(e) => setDraft({ ...draft, toDate: e.target.value })} />
        </FormField>
        <FormField label="Flag">
          <select value={draft.flag} onChange={(e) => setDraft({ ...draft, flag: e.target.value })}>
            <option value="">Any</option>
            <option value="DUPLICATE">Duplicate</option>
            <option value="WEEKEND">Weekend</option>
          </select>
        </FormField>
        <div className="filter-actions">
          <button type="submit" className="btn btn-primary">
            Apply
          </button>
          <button type="button" className="btn" onClick={clearFilters}>
            Clear filters
          </button>
        </div>
      </form>

      <DataTable
        caption="Review queue"
        columns={columns}
        rows={rows}
        getRowId={(r) => r.claimId}
        rowLabel={(r) => `claim ${r.claimNumber}`}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyTitle="No claims match these filters."
        onRowClick={(r) => navigate(`/review-queue/${r.claimId}`)}
        selectable
        selected={selected}
        onSelectionChange={(s) => {
          setSelected(new Set([...s].slice(0, 100)))
        }}
        rowStatus={(r) => rowMessages[r.claimId]}
        page={data?.page}
        pageSize={data?.pageSize}
        total={data?.total}
        onPageChange={(page) => {
          setSelected(new Set())
          setQ({ page }, { resetPage: false })
        }}
      />

      {selected.size > 0 && (
        <BulkActionBar label="Bulk approve">
          <span>
            {selected.size} selected · <MoneyDisplay value={selectedTotal} />
          </span>
          <button type="button" className="btn" onClick={() => setSelected(new Set())}>
            Clear selection
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setConfirmOpen(true)}
            disabled={bulk.pending}
          >
            Approve selected ({selected.size})
          </button>
        </BulkActionBar>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="Approve selected claims?"
        confirmLabel={`Approve ${selected.size}`}
        pending={bulk.pending}
        onConfirm={doBulk}
        onCancel={() => setConfirmOpen(false)}
      >
        <p>
          Approve {selected.size} claim(s) totalling <MoneyDisplay value={selectedTotal} />.
        </p>
        {flaggedCount > 0 && (
          <p className="hint-warn">
            {flaggedCount} of them {flaggedCount === 1 ? 'has' : 'have'} a duplicate or weekend flag.
          </p>
        )}
      </ConfirmDialog>
    </section>
  )
}
