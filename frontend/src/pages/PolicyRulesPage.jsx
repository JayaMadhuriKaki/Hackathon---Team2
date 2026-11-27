import { useState } from 'react'
import { policyApi } from '../api/index.js'
import { DataTable } from '../components/DataTable.jsx'
import { DateDisplay } from '../components/Display.jsx'
import { FormField } from '../components/FormField.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useQuery } from '../hooks/useQuery.js'
import { fieldErrors, messageFor } from '../utils/errors.js'
import { formatInr } from '../utils/money.js'

/** Related POL rule for each config key (CLAUDE.md §2.2). */
const RELATED = {
  'meals.max_per_day': 'POL-001',
  'hotel.max_per_night.tier1': 'POL-002',
  'hotel.tier1_cities': 'POL-002',
  'hotel.max_per_night.other': 'POL-003',
  'local_cab.max_per_trip': 'POL-004',
  'receipt.mandatory_above': 'POL-005',
  'duplicate.match_fields': 'POL-006',
  'duplicate.match_receipt_hash': 'POL-007',
  'weekend.flag_for_manager': 'POL-008',
  'duplicate.match_date_receipt_no': 'POL-009',
}

function kindOf(rule) {
  const v = rule.value
  if (typeof v === 'boolean') return 'boolean'
  if (Array.isArray(v)) return 'list'
  if (rule.ruleKey.includes('rate')) return 'rate'
  if (typeof v === 'string' || typeof v === 'number') return /^\d+(\.\d+)?$/.test(String(v)) ? 'money' : 'text'
  return 'json'
}

function showValue(rule) {
  const k = kindOf(rule)
  if (k === 'boolean') return rule.value ? 'Yes' : 'No'
  if (k === 'list') return rule.value.join(', ')
  if (k === 'money') return formatInr(rule.value)
  if (k === 'rate') return `${Number(rule.value) * 100}%`
  if (k === 'json') return JSON.stringify(rule.value)
  return String(rule.value)
}

function toInput(rule) {
  const k = kindOf(rule)
  if (k === 'list') return rule.value.join(', ')
  if (k === 'boolean') return rule.value ? 'true' : 'false'
  if (k === 'json') return JSON.stringify(rule.value)
  return String(rule.value)
}

/** Parses the editor input by type; returns { value } or { error }. */
export function parseRuleInput(rule, input, rules = []) {
  const k = kindOf(rule)
  const s = input.trim()
  if (k === 'boolean') return { value: s === 'true' }
  if (k === 'list') {
    const items = s.split(',').map((x) => x.trim()).filter(Boolean)
    return items.length ? { value: items } : { error: 'Enter at least one value, separated by commas.' }
  }
  if (k === 'rate') {
    const n = Number(s)
    return n > 0 && n <= 1 ? { value: s } : { error: 'Enter a number greater than 0 and at most 1.' }
  }
  if (k === 'money') {
    if (!/^\d+(\.\d{1,2})?$/.test(s)) return { error: 'Enter an amount with at most 2 decimals.' }
    const n = Number(s)
    if (rule.ruleKey !== 'receipt.mandatory_above' && n <= 0) return { error: 'Amount must be greater than 0.' }
    const auditor = rules.find((r) => r.ruleKey === 'approval.auditor_threshold')
    const head = rules.find((r) => r.ruleKey === 'approval.finance_head_threshold')
    if (rule.ruleKey === 'approval.finance_head_threshold' && auditor && n <= Number(auditor.value)) {
      return { error: 'Must be greater than the finance auditor threshold.' }
    }
    if (rule.ruleKey === 'approval.auditor_threshold' && head && n >= Number(head.value)) {
      return { error: 'Must be less than the finance head threshold.' }
    }
    return { value: s }
  }
  if (k === 'json') {
    try {
      return { value: JSON.parse(s) }
    } catch {
      return { error: 'Enter valid JSON.' }
    }
  }
  return s ? { value: s } : { error: 'Enter a value.' }
}

/** S-13 PolicyRulesPage (API-034, API-035): CL-10, B-08. Finance head / admin only. */
export default function PolicyRulesPage() {
  useDocumentTitle('Policy rules')
  const toast = useToast()
  const { data, error, loading, reload } = useQuery((opts) => policyApi.list(opts), [])
  const [editing, setEditing] = useState(null)
  const [input, setInput] = useState('')
  const [inputError, setInputError] = useState(null)
  const [pending, setPending] = useState(false)

  function startEdit(rule) {
    setEditing(rule)
    setInput(toInput(rule))
    setInputError(null)
  }

  async function save(e) {
    e.preventDefault()
    if (pending) return
    const parsed = parseRuleInput(editing, input, data ?? [])
    if (parsed.error) {
      setInputError(parsed.error)
      return
    }
    setPending(true)
    try {
      await policyApi.update(editing.ruleKey, parsed.value)
      toast.success(`${editing.ruleKey} updated.`)
      setEditing(null)
      reload()
    } catch (err) {
      const fe = fieldErrors(err)
      setInputError(Object.values(fe)[0] ?? (err.code === 'VALIDATION_FAILED' ? err.message : messageFor(err)))
    } finally {
      setPending(false)
    }
  }

  const columns = [
    { key: 'ruleKey', header: 'Key', render: (r) => <code>{r.ruleKey}</code> },
    { key: 'value', header: 'Current value', render: showValue },
    { key: 'related', header: 'Rule', render: (r) => RELATED[r.ruleKey] ?? (r.ruleKey.startsWith('approval') ? 'Routing' : r.ruleKey.startsWith('sample') ? 'FR-011' : '—') },
    { key: 'configVersion', header: 'Version', align: 'right' },
    { key: 'updatedAt', header: 'Last changed', render: (r) => <DateDisplay value={r.updatedAt} /> },
    {
      key: 'edit',
      header: 'Actions',
      render: (r) => (
        <button type="button" className="btn btn-sm" onClick={() => startEdit(r)}>
          Edit {r.ruleKey}
        </button>
      ),
    },
  ]

  const kind = editing ? kindOf(editing) : null

  return (
    <section>
      <h1>Policy rules</h1>
      <p className="banner banner-info">
        Changes apply to new submissions only; submitted claims keep their rules. Every change is recorded in the audit
        trail.
      </p>
      {editing && (
        <form className="card" onSubmit={save} aria-label={`Edit ${editing.ruleKey}`}>
          <h2>
            Edit <code>{editing.ruleKey}</code>
          </h2>
          <FormField
            label="New value"
            error={inputError}
            hint={
              kind === 'list'
                ? 'Separate values with commas.'
                : kind === 'rate'
                  ? 'A fraction between 0 and 1 (0.10 = 10%).'
                  : kind === 'money'
                    ? 'Rupees, at most 2 decimals.'
                    : undefined
            }
          >
            {kind === 'boolean' ? (
              <select value={input} onChange={(e) => setInput(e.target.value)}>
                <option value="true">Yes</option>
                <option value="false">No</option>
              </select>
            ) : (
              <input type="text" value={input} onChange={(e) => setInput(e.target.value)} />
            )}
          </FormField>
          <div className="form-actions">
            <button type="button" className="btn" onClick={() => setEditing(null)} disabled={pending}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={pending}>
              {pending ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      )}
      <DataTable
        caption="Policy configuration"
        columns={columns}
        rows={data}
        getRowId={(r) => r.ruleKey}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyTitle="No policy rules configured."
      />
    </section>
  )
}
