import { useCallback, useState } from 'react'
import { delegationsApi } from '../api/index.js'
import { ConfirmDialog } from '../components/ConfirmDialog.jsx'
import { DataTable } from '../components/DataTable.jsx'
import { DateDisplay, StatusBadge } from '../components/Display.jsx'
import { FormField } from '../components/FormField.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useMutation } from '../hooks/useMutation.js'
import { useQuery } from '../hooks/useQuery.js'
import { todayIst } from '../utils/dates.js'
import { fieldErrors, messageFor } from '../utils/errors.js'
import { validateDelegation } from '../utils/validation.js'

/** Uses the server's `active` flag (computed in IST, contract DelegationView); dates only split the rest. */
export function delegationStatus(d, today = todayIst()) {
  if (d.cancelledAt) return 'CANCELLED'
  if (d.active) return 'ACTIVE'
  if (d.endDate < today) return 'ENDED'
  if (d.startDate > today) return 'SCHEDULED'
  return 'ACTIVE'
}

const EMPTY = { delegateId: '', startDate: '', endDate: '' }

/** S-08 DelegationsPage (API-019..021, API-038): FR-008, CL-13, ASM-007. */
export default function DelegationsPage() {
  useDocumentTitle('Delegations')
  const toast = useToast()
  const listQ = useQuery((opts) => delegationsApi.list({ pageSize: 100 }, opts), [])
  const delegatesQ = useQuery((opts) => delegationsApi.eligibleDelegates(opts), [])
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [pending, setPending] = useState(false)
  const [cancelTarget, setCancelTarget] = useState(null)
  const cancel = useMutation(useCallback((id) => delegationsApi.cancel(id), []))

  async function onSubmit(e) {
    e.preventDefault()
    if (pending) return
    const errs = validateDelegation(form)
    setErrors(errs)
    if (Object.keys(errs).length) return
    setPending(true)
    try {
      await delegationsApi.create(form)
      toast.success('Delegation created.')
      setForm(EMPTY)
      listQ.reload()
    } catch (err) {
      if (err.code === 'INVALID_DELEGATE') setErrors({ delegateId: messageFor(err) })
      else if (err.code === 'DELEGATION_OVERLAP') setErrors({ startDate: messageFor(err), endDate: ' ' })
      else if (err.code === 'VALIDATION_FAILED') setErrors(fieldErrors(err))
      else toast.error(messageFor(err), err.traceId)
    } finally {
      setPending(false)
    }
  }

  async function doCancel() {
    try {
      await cancel.run(cancelTarget.delegationId)
      toast.success('Delegation cancelled.')
      setCancelTarget(null)
      listQ.reload()
    } catch (err) {
      setCancelTarget(null)
      toast.error(messageFor(err), err.traceId)
    }
  }

  const today = todayIst()
  const columns = [
    {
      key: 'direction',
      header: 'Type',
      render: (d) => (d.direction === 'GIVEN' ? 'You delegated' : 'Delegated to you'),
    },
    {
      key: 'person',
      header: 'Person',
      render: (d) => (d.direction === 'GIVEN' ? d.delegate?.name : d.delegator?.name),
    },
    { key: 'startDate', header: 'Start', render: (d) => <DateDisplay value={d.startDate} dateOnly /> },
    { key: 'endDate', header: 'End (inclusive)', render: (d) => <DateDisplay value={d.endDate} dateOnly /> },
    { key: 'status', header: 'Status', render: (d) => <StatusBadge status={delegationStatus(d, today)} /> },
    {
      key: 'actions',
      header: 'Actions',
      render: (d) =>
        d.direction === 'GIVEN' && !d.cancelledAt && d.endDate >= today ? (
          <button type="button" className="btn btn-sm" onClick={() => setCancelTarget(d)}>
            Cancel delegation
          </button>
        ) : null,
    },
  ]

  return (
    <section>
      <h1>Delegations</h1>
      <p className="hint">
        While you are on leave, your delegate can act on manager approvals assigned to you during these dates. You
        can still act yourself. Delegates must be a finance auditor or finance head.
      </p>

      <div className="two-col">
        <form className="card" onSubmit={onSubmit} noValidate aria-label="New delegation">
          <h2>New delegation</h2>
          <FormField label="Delegate" error={errors.delegateId} required>
            <select value={form.delegateId} onChange={(e) => setForm({ ...form, delegateId: e.target.value })}>
              <option value="">{delegatesQ.loading ? 'Loading…' : 'Choose…'}</option>
              {(delegatesQ.data ?? []).map((p) => (
                <option key={p.userId} value={p.userId}>
                  {p.name} ({p.employeeCode})
                </option>
              ))}
            </select>
          </FormField>
          <div className="field-row">
            <FormField label="Start date" error={errors.startDate} required>
              <input
                type="date"
                min={today}
                value={form.startDate}
                onChange={(e) => setForm({ ...form, startDate: e.target.value })}
              />
            </FormField>
            <FormField label="End date" error={errors.endDate?.trim() ? errors.endDate : undefined} required>
              <input
                type="date"
                min={form.startDate || today}
                value={form.endDate}
                onChange={(e) => setForm({ ...form, endDate: e.target.value })}
              />
            </FormField>
          </div>
          <button type="submit" className="btn btn-primary" disabled={pending}>
            {pending ? 'Saving…' : 'Create delegation'}
          </button>
        </form>

        <div>
          <h2>Your delegations</h2>
          <DataTable
            caption="Your delegations"
            columns={columns}
            rows={listQ.data?.items}
            getRowId={(d) => d.delegationId}
            loading={listQ.loading}
            error={listQ.error}
            onRetry={listQ.reload}
            emptyTitle="You have no delegations."
          />
        </div>
      </div>

      <ConfirmDialog
        open={Boolean(cancelTarget)}
        title="Cancel delegation?"
        confirmLabel="Cancel delegation"
        tone="danger"
        pending={cancel.pending}
        onConfirm={doCancel}
        onCancel={() => setCancelTarget(null)}
      >
        {cancelTarget && (
          <p>
            {cancelTarget.delegate?.name} will no longer be able to act for you from {cancelTarget.startDate} to{' '}
            {cancelTarget.endDate}.
          </p>
        )}
      </ConfirmDialog>
    </section>
  )
}
