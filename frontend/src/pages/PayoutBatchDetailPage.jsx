import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { payoutApi } from '../api/index.js'
import { ConfirmDialog } from '../components/ConfirmDialog.jsx'
import { DataTable } from '../components/DataTable.jsx'
import { DateDisplay, MoneyDisplay, StatusBadge } from '../components/Display.jsx'
import { ErrorState, LoadingState } from '../components/States.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useMutation } from '../hooks/useMutation.js'
import { useQuery } from '../hooks/useQuery.js'
import { messageFor } from '../utils/errors.js'

function fallbackFileName(batch) {
  const d = (batch.exportedAt ?? new Date().toISOString()).slice(0, 10).replaceAll('-', '')
  return `EXPENSEFLOW_PAYOUT_${batch.batchNumber}_${d}.csv`
}

/** S-10 PayoutBatchDetailPage (API-027..029): FR-013, FR-014, CL-06, §2.8. */
export default function PayoutBatchDetailPage() {
  const { batchId } = useParams()
  const toast = useToast()
  const { data, error, loading, reload } = useQuery((opts) => payoutApi.get(batchId, opts), [batchId])
  const batch = data?.batch
  useDocumentTitle(batch ? `Batch ${batch.batchNumber}` : 'Payout batch')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const exportM = useMutation(useCallback((id, key) => payoutApi.export(id, key), []))

  async function doExport() {
    try {
      const summary = await exportM.run(batchId)
      if (!summary) return
      setConfirmOpen(false)
      toast.success(`Batch ${summary.batchNumber} exported. ${summary.rowCount} claim(s) marked Paid.`)
      reload()
    } catch (err) {
      setConfirmOpen(false)
      toast.error(messageFor(err), err.traceId)
      if (err.code === 'STATE_CONFLICT') reload()
    }
  }

  async function download() {
    if (downloading) return
    setDownloading(true)
    try {
      const { blob, fileName } = await payoutApi.download(batchId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName ?? fallbackFileName(batch)
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (err) {
      toast.error(err.code === 'STATE_CONFLICT' ? 'Export the batch before downloading.' : messageFor(err), err.traceId)
    } finally {
      setDownloading(false)
    }
  }

  if (loading && !data) return <LoadingState />
  if (error && !data) {
    return (
      <section>
        <h1>Payout batch</h1>
        <ErrorState error={error} onRetry={reload} />
        <Link to="/payout-batches">Back to batches</Link>
      </section>
    )
  }

  const exported = batch.status === 'EXPORTED'
  const columns = [
    { key: 'claimNumber', header: 'Claim' },
    { key: 'employeeCode', header: 'Employee ID', render: (c) => c.claimant?.employeeCode },
    { key: 'name', header: 'Name', render: (c) => c.claimant?.name },
    { key: 'department', header: 'Department' },
    { key: 'status', header: 'Status', render: (c) => <StatusBadge status={c.status} /> },
    { key: 'total', header: 'Amount', align: 'right', render: (c) => <MoneyDisplay value={c.total} /> },
  ]

  return (
    <section>
      <div className="page-head">
        <h1>Batch {batch.batchNumber}</h1>
        <Link to="/payout-batches">Back to batches</Link>
      </div>

      <dl className="claim-header">
        <div>
          <dt>Status</dt>
          <dd>
            <StatusBadge status={batch.status} />
          </dd>
        </div>
        <div>
          <dt>Rows</dt>
          <dd>{batch.claimCount}</dd>
        </div>
        <div>
          <dt>Total</dt>
          <dd>
            <MoneyDisplay value={batch.totalAmount} className="money-lg" />
          </dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>
            <DateDisplay value={batch.createdAt} />
          </dd>
        </div>
        <div>
          <dt>Exported</dt>
          <dd>
            <DateDisplay value={batch.exportedAt} />
          </dd>
        </div>
      </dl>

      {exported ? (
        <div className="card control-summary">
          <h2>Control summary</h2>
          <dl>
            <dt>File</dt>
            <dd className="mono">{batch.fileName}</dd>
            <dt>Rows</dt>
            <dd>{batch.claimCount}</dd>
            <dt>Total amount</dt>
            <dd>
              <MoneyDisplay value={batch.totalAmount} />
            </dd>
            <dt>SHA-256</dt>
            <dd className="mono hash">
              {batch.fileSha256}{' '}
              <button
                type="button"
                className="btn btn-sm"
                onClick={() => navigator.clipboard?.writeText(batch.fileSha256).then(() => toast.success('Hash copied.'))}
              >
                Copy
              </button>
            </dd>
          </dl>
          <button type="button" className="btn btn-primary" onClick={download} disabled={downloading}>
            {downloading ? 'Downloading…' : 'Download CSV'}
          </button>
        </div>
      ) : (
        <div className="card">
          <p>Exporting generates the bank file, locks this batch and marks every claim in it as Paid.</p>
          <button type="button" className="btn btn-primary" onClick={() => setConfirmOpen(true)} disabled={exportM.pending}>
            Export batch
          </button>
        </div>
      )}

      <h2>Claims</h2>
      <DataTable
        caption="Claims in this batch"
        columns={columns}
        rows={data.claims}
        getRowId={(c) => c.claimId}
        emptyTitle="This batch has no claims."
      />

      <ConfirmDialog
        open={confirmOpen}
        title="Export batch?"
        confirmLabel="Export"
        tone="danger"
        pending={exportM.pending}
        onConfirm={doExport}
        onCancel={() => setConfirmOpen(false)}
      >
        <p>
          Export locks this batch and marks {batch.claimCount} claim(s) as Paid. This cannot be undone.
        </p>
      </ConfirmDialog>
    </section>
  )
}
