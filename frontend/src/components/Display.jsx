import { formatInr } from '../utils/money.js'
import { formatDate, formatTimestamp } from '../utils/dates.js'

/** ₹1,00,000.00 from an API money string (§4.3). */
export function MoneyDisplay({ value, className = '' }) {
  return <span className={`money ${className}`}>{formatInr(value)}</span>
}

/** Timestamp (UTC → IST) or date-only (shown as-is). */
export function DateDisplay({ value, dateOnly = false }) {
  if (!value) return <span className="muted">—</span>
  return <time dateTime={value}>{dateOnly ? formatDate(value) : formatTimestamp(value)}</time>
}

const STATUS = {
  // Claim states
  DRAFT: ['Draft', 'neutral'],
  SUBMITTED: ['Submitted', 'info'],
  AUTO_APPROVED: ['Auto-approved', 'ok'],
  AUTO_REJECTED: ['Auto-rejected', 'bad'],
  PENDING_MANAGER: ['Pending manager', 'warn'],
  PENDING_AUDITOR: ['Pending auditor', 'warn'],
  PENDING_FINANCE_HEAD: ['Pending finance head', 'warn'],
  SENT_BACK: ['Sent back', 'warn'],
  REJECTED: ['Rejected', 'bad'],
  SAMPLE_AUDIT_PENDING: ['Sample audit', 'info'],
  APPROVED: ['Approved', 'ok'],
  IN_PAYOUT_BATCH: ['In payout batch', 'info'],
  PAID: ['Paid', 'ok'],
  // Step states
  WAITING: ['Waiting', 'neutral'],
  PENDING: ['Pending', 'warn'],
  SKIPPED: ['Skipped', 'neutral'],
  SUPERSEDED: ['Superseded', 'neutral'],
  // Batch states
  CREATED: ['Created', 'info'],
  EXPORTED: ['Exported', 'ok'],
  // Delegation
  ACTIVE: ['Active', 'ok'],
  SCHEDULED: ['Scheduled', 'info'],
  ENDED: ['Ended', 'neutral'],
  CANCELLED: ['Cancelled', 'neutral'],
}

const ICON = { ok: '✓', bad: '✕', warn: '●', info: '◆', neutral: '○' }

export function statusLabel(status) {
  return STATUS[status]?.[0] ?? String(status ?? '').replaceAll('_', ' ').toLowerCase()
}

/** Badge with text + icon, never colour alone (A11Y-06). */
export function StatusBadge({ status }) {
  const [label, tone] = STATUS[status] ?? [statusLabel(status), 'neutral']
  return (
    <span className={`badge badge-${tone}`}>
      <span aria-hidden="true">{ICON[tone]}</span> {label}
    </span>
  )
}

const FLAG_LABEL = { DUPLICATE: 'Duplicate', WEEKEND: 'Weekend' }

export function FlagList({ flags }) {
  if (!flags?.length) return <span className="muted">—</span>
  return (
    <span className="flags">
      {flags.map((f) => (
        <span key={f} className={`flag flag-${f.toLowerCase()}`}>
          <span aria-hidden="true">⚑</span> {FLAG_LABEL[f] ?? f}
        </span>
      ))}
    </span>
  )
}

export const CATEGORY_LABEL = { MEALS: 'Meals', HOTEL: 'Hotel', LOCAL_CAB: 'Local cab' }

export const STEP_LABEL = { MANAGER: 'Manager', FINANCE_AUDITOR: 'Finance auditor', FINANCE_HEAD: 'Finance head' }

export const ROLE_LABEL = {
  EMPLOYEE: 'Employee',
  FINANCE_AUDITOR: 'Finance auditor',
  FINANCE_HEAD: 'Finance head',
  ADMIN: 'Admin',
}
