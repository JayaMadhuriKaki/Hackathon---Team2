import { useEffect, useId, useRef, useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
import { nightsBetween, todayIst } from '../utils/dates.js'
import { formatInr } from '../utils/money.js'
import { receiptRequired, validateLine } from '../utils/validation.js'
import { CATEGORY_LABEL } from './Display.jsx'
import { ErrorSummary, FormField } from './FormField.jsx'

export const EMPTY_LINE = {
  category: '',
  amount: '',
  expenseDate: '',
  vendor: '',
  receiptNumber: '',
  city: '',
  checkInDate: '',
  checkOutDate: '',
}

export function lineToForm(line) {
  if (!line) return { ...EMPTY_LINE }
  return {
    category: line.category ?? '',
    amount: line.amount ?? '',
    expenseDate: line.expenseDate ?? '',
    vendor: line.vendor ?? '',
    receiptNumber: line.receiptNumber ?? '',
    city: line.city ?? '',
    checkInDate: line.checkInDate ?? '',
    checkOutDate: line.checkOutDate ?? '',
  }
}

/** Request body for API-007 / API-008 (ClaimDtos.LineItemRequest). Hotel expenseDate = check-in (ASM-S-03). */
export function formToRequest(f) {
  const hotel = f.category === 'HOTEL'
  return {
    category: f.category,
    amount: String(f.amount).trim(),
    expenseDate: hotel ? f.checkInDate : f.expenseDate,
    vendor: f.vendor.trim(),
    receiptNumber: f.receiptNumber.trim(),
    city: hotel ? f.city.trim() : null,
    checkInDate: hotel ? f.checkInDate : null,
    checkOutDate: hotel ? f.checkOutDate : null,
  }
}

/** Receipt picker with client-side type/size checks (allow-list from API-037). */
export function ReceiptUpload({ file, existing, onChange, error }) {
  const { config } = useAuth()
  const id = useId()
  const [localError, setLocalError] = useState(null)
  const types = config?.allowedReceiptTypes ?? ['application/pdf', 'image/jpeg', 'image/png']
  const maxBytes = config?.maxReceiptBytes ?? 5242880

  function onPick(e) {
    const f = e.target.files?.[0]
    setLocalError(null)
    if (!f) return
    if (!types.includes(f.type)) {
      setLocalError('This file type is not accepted. Use PDF, JPG or PNG.')
      e.target.value = ''
      return
    }
    if (f.size > maxBytes) {
      setLocalError(`The file is too large. Maximum size is ${Math.round(maxBytes / 1048576)} MB.`)
      e.target.value = ''
      return
    }
    onChange(f)
  }

  const shown = localError ?? error
  return (
    <div className={`field ${shown ? 'field-invalid' : ''}`}>
      <label htmlFor={id}>Receipt (PDF, JPG or PNG, up to {Math.round(maxBytes / 1048576)} MB)</label>
      <input
        id={id}
        type="file"
        accept={types.join(',')}
        onChange={onPick}
        aria-invalid={shown ? 'true' : undefined}
        aria-describedby={`${id}-status`}
      />
      <p id={`${id}-status`} className="hint">
        {file ? `Selected: ${file.name}` : existing ? 'A receipt is attached. Choose a file to replace it.' : 'No receipt attached.'}
      </p>
      {shown && <p className="field-error">{shown}</p>}
    </div>
  )
}

/**
 * Line fields of S-03 (frontend/SPEC.md §4 S-03). Client validation mirrors the backend; the UI never
 * shows or checks spending limits — policy results come only from the server.
 */
export function LineItemForm({ initial, existingReceipt, serverErrors, pending, onSave, onCancel, onDirtyChange }) {
  const { config } = useAuth()
  const [form, setForm] = useState(() => lineToForm(initial))
  const [file, setFile] = useState(null)
  const [clientErrors, setErrors] = useState({})
  const [touched, setTouched] = useState(false)
  const today = todayIst()
  const categories = config?.categories ?? ['MEALS', 'HOTEL', 'LOCAL_CAB']
  const tier1 = config?.tier1Cities ?? []
  const listId = useId()
  // Server field errors (400 VALIDATION_FAILED) are shown until the next save clears them.
  const errors = { ...(serverErrors ?? {}), ...clientErrors }
  const formRef = useRef(null)

  // Bring the form into view and focus its first field when it opens (matters on phones).
  useEffect(() => {
    formRef.current?.scrollIntoView?.({ block: 'start' })
    formRef.current?.querySelector('select')?.focus({ preventScroll: true })
  }, [])

  function set(field, value) {
    setForm((f) => {
      const next = { ...f, [field]: value }
      if (field === 'category' && value !== 'HOTEL') {
        next.city = ''
        next.checkInDate = ''
        next.checkOutDate = ''
      }
      return next
    })
    if (!touched) {
      setTouched(true)
      onDirtyChange?.(true)
    }
  }

  function submit(e) {
    e.preventDefault()
    const errs = validateLine(form, today)
    setErrors(errs)
    if (Object.keys(errs).length) return
    onSave(formToRequest(form), file)
  }

  const hotel = form.category === 'HOTEL'
  const nights = hotel ? nightsBetween(form.checkInDate, form.checkOutDate) : null
  const needsReceipt = receiptRequired(form.amount, config?.receiptMandatoryAbove)

  return (
    <form ref={formRef} className="line-form" onSubmit={submit} noValidate aria-label={initial ? `Edit line ${initial.lineNo}` : 'New line'}>
      <ErrorSummary errors={errors} />
      <FormField label="Category" error={errors.category} required>
        <select value={form.category} onChange={(e) => set('category', e.target.value)}>
          <option value="">Choose…</option>
          {categories.map((c) => (
            <option key={c} value={c}>
              {CATEGORY_LABEL[c] ?? c}
            </option>
          ))}
        </select>
      </FormField>
      <FormField label="Amount (₹)" error={errors.amount} required>
        <input
          type="text"
          inputMode="decimal"
          autoComplete="off"
          value={form.amount}
          onChange={(e) => set('amount', e.target.value)}
        />
      </FormField>
      {!hotel && (
        <FormField label="Expense date" error={errors.expenseDate} required>
          <input type="date" max={today} value={form.expenseDate} onChange={(e) => set('expenseDate', e.target.value)} />
        </FormField>
      )}
      <FormField label="Vendor" error={errors.vendor} required>
        <input type="text" maxLength={120} value={form.vendor} onChange={(e) => set('vendor', e.target.value)} />
      </FormField>
      <FormField label="Receipt number" error={errors.receiptNumber} required>
        <input
          type="text"
          maxLength={60}
          value={form.receiptNumber}
          onChange={(e) => set('receiptNumber', e.target.value)}
        />
      </FormField>
      {hotel && (
        <fieldset className="hotel-fields">
          <legend>Hotel stay</legend>
          <FormField label="City" error={errors.city} required hint="Tier-1 cities are suggested; any city is allowed.">
            <input type="text" list={listId} maxLength={100} value={form.city} onChange={(e) => set('city', e.target.value)} />
          </FormField>
          <datalist id={listId}>
            {tier1.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <div className="field-row">
            <FormField label="Check-in" error={errors.checkInDate} required>
              <input type="date" max={today} value={form.checkInDate} onChange={(e) => set('checkInDate', e.target.value)} />
            </FormField>
            <FormField label="Check-out" error={errors.checkOutDate} required>
              <input
                type="date"
                min={form.checkInDate || undefined}
                value={form.checkOutDate}
                onChange={(e) => set('checkOutDate', e.target.value)}
              />
            </FormField>
          </div>
          <p className="hint" aria-live="polite">
            Nights: {nights && nights > 0 ? nights : '—'}
          </p>
        </fieldset>
      )}
      <ReceiptUpload
        file={file}
        existing={existingReceipt}
        error={errors.receipt}
        onChange={(f) => {
          setFile(f)
          if (!touched) {
            setTouched(true)
            onDirtyChange?.(true)
          }
        }}
      />
      {needsReceipt && !file && !existingReceipt && (
        <p className="hint hint-warn">
          A receipt is required to submit lines above {formatInr(config?.receiptMandatoryAbove)}.
        </p>
      )}
      <div className="form-actions">
        <button type="button" className="btn" onClick={onCancel} disabled={pending}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? 'Saving…' : 'Save line'}
        </button>
      </div>
    </form>
  )
}
