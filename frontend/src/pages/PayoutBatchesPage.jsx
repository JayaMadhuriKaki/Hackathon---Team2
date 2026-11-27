import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { payoutApi } from '../api/index.js'
import { ConfirmDialog } from '../components/ConfirmDialog.jsx'
import { BulkActionBar, DataTable } from '../components/DataTable.jsx'
import { DateDisplay, MoneyDisplay, StatusBadge } from '../components/Display.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useMutation } from '../hooks/useMutation.js'
import { useQuery } from '../hooks/useQuery.js'
import { useUrlQuery } from '../hooks/useUrlQuery.js'
import { messageFor } from '../utils/errors.js'
import { sumMoney } from '../utils/money.js'

/** S-09 PayoutBatchesPage (API-024..026): FR-012, §2.8. */
export default function PayoutBatchesPage() {
  useDocumentTitle('Payout batches')
  const navigate = useNavigate()
  const toast = useToast()
  const [q, setQ] = useUrlQuery({ page: 1, pageSize: 20 })
  const [creating, setCreating] = useState(false)
  const [eligiblePage, setEligiblePage] = useState(1)
  const [selected, setSelected] = useState(new Set())
  const [selectedRows, setSelectedRows] = useState({})
  const [confirmOpen, setConfirmOpen] = useState(false)

  const batchesQ = useQuery((opts) => payoutApi.list({ page: q.page, pageSize: q.pageSize }, opts), [
    q.page,
    q.pageSize,
  ])
  const eligibleQ = useQuery((opts) => payoutApi.eligible({ page: eligiblePage, pageSize: 100 }, opts), [
    eligiblePage,
  ], { enabled: creating })
  const create = useMutation(useCallback((ids, key) => payoutApi.create(ids, key), []))

  const total = sumMoney(Object.values(selectedRows).map((r) => r.total))

  function onSelectionChange(next) {
    setSelected(next)
    const rows = { ...selectedRows }
    for (const r of eligibleQ.data?.items ?? []) {
      if (next.has(r.claimId)) rows[r.claimId] = r
      else delete rows[r.claimId]
    }
    for (const id of Object.keys(rows)) if (!next.has(id)) delete rows[id]
    setSelectedRows(rows)
  }

  async function doCreate() {
    try {
      // API-026 returns BatchDetail { batch, claims } (architecture/api-contracts.yaml)
      const created = await create.run([...selected])
      if (!created) return
      const batch = created.batch ?? created
      setConfirmOpen(false)
      toast.success(`Batch ${batch.batchNumber} created.`)
      navigate(`/payout-batches/${batch.batchId}`)
    } catch (err) {
      setConfirmOpen(false)
      toast.error(messageFor(err), err.traceId)
      if (err.code === 'CLAIM_NOT_ELIGIBLE_FOR_PAYOUT') {
        setSelected(new Set())
        setSelectedRows({})
        eligibleQ.reload()
      }
    }
  }

  const batchColumns = [
    { key: 'batchNumber', header: 'Batch' },
    { key: 'status', header: 'Status', render: (b) => <StatusBadge status={b.status} /> },
    { key: 'claimCount', header: 'Rows', align: 'right' },
    { key: 'totalAmount', header: 'Total', align: 'right', render: (b) => <MoneyDisplay value={b.totalAmount} /> },
    { key: 'createdAt', header: 'Created', render: (b) => <DateDisplay value={b.createdAt} /> },
    { key: 'exportedAt', header: 'Exported', render: (b) => <DateDisplay value={b.exportedAt} /> },
  ]

  const eligibleColumns = [
    { key: 'claimNumber', header: 'Claim' },
    {
      key: 'claimant',
      header: 'Employee',
      render: (c) => (
        <>
          {c.claimant?.name} <span className="muted">({c.claimant?.employeeCode})</span>
        </>
      ),
    },
    { key: 'department', header: 'Department' },
    { key: 'approvedAt', header: 'Approved', render: (c) => <DateDisplay value={c.approvedAt} /> },
    { key: 'total', header: 'Amount', align: 'right', render: (c) => <MoneyDisplay value={c.total} /> },
  ]

  return (
    <section>
      <div className="page-head">
        <h1>Payout batches</h1>
        {!creating && (
          <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
            New batch
          </button>
        )}
      </div>

      {creating && (
        <div className="card">
          <div className="page-head">
            <h2>Approved claims waiting for payout</h2>
            <button
              type="button"
              className="btn btn-sm"
              onClick={() => {
                setCreating(false)
                setSelected(new Set())
                setSelectedRows({})
              }}
            >
              Close
            </button>
          </div>
          <p className="hint">Claims held for sample audit are not listed until an auditor reviews them.</p>
          <DataTable
            caption="Eligible claims"
            columns={eligibleColumns}
            rows={eligibleQ.data?.items}
            getRowId={(c) => c.claimId}
            rowLabel={(c) => `claim ${c.claimNumber}`}
            loading={eligibleQ.loading}
            error={eligibleQ.error}
            onRetry={eligibleQ.reload}
            emptyTitle="No approved claims are waiting for payout."
            selectable
            selected={selected}
            onSelectionChange={onSelectionChange}
            page={eligibleQ.data?.page}
            pageSize={eligibleQ.data?.pageSize}
            total={eligibleQ.data?.total}
            onPageChange={setEligiblePage}
          />
          {selected.size > 0 && (
            <BulkActionBar label="Create batch">
              <span>
                {selected.size} claim(s) · <MoneyDisplay value={total} />
              </span>
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setConfirmOpen(true)}
                disabled={create.pending}
              >
                Create batch
              </button>
            </BulkActionBar>
          )}
        </div>
      )}

      <h2>Batches</h2>
      <DataTable
        caption="Payout batches"
        columns={batchColumns}
        rows={batchesQ.data?.items}
        getRowId={(b) => b.batchId}
        rowLabel={(b) => b.batchNumber}
        loading={batchesQ.loading}
        error={batchesQ.error}
        onRetry={batchesQ.reload}
        emptyTitle="No payout batches yet."
        onRowClick={(b) => navigate(`/payout-batches/${b.batchId}`)}
        page={batchesQ.data?.page}
        pageSize={batchesQ.data?.pageSize}
        total={batchesQ.data?.total}
        onPageChange={(page) => setQ({ page }, { resetPage: false })}
      />

      <ConfirmDialog
        open={confirmOpen}
        title="Create payout batch?"
        confirmLabel="Create batch"
        pending={create.pending}
        onConfirm={doCreate}
        onCancel={() => setConfirmOpen(false)}
      >
        <p>
          Create a batch of {selected.size} claim(s) totalling <MoneyDisplay value={total} />. Each claim can belong to
          only one batch.
        </p>
      </ConfirmDialog>
    </section>
  )
}
