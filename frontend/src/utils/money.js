// Money helpers (CLAUDE.md §4.3). The API sends money as strings with 2 decimals.
// Sums are done in integer paise, never in floating-point rupees.

const MONEY_RE = /^\d{1,10}(\.\d{1,2})?$/

const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** "27450.5" -> 2745050. Returns null for anything that is not a non-negative amount. */
export function toPaise(value) {
  if (value === null || value === undefined) return null
  const s = String(value).trim()
  if (!MONEY_RE.test(s)) return null
  const [whole, frac = ''] = s.split('.')
  return Number(whole) * 100 + Number(frac.padEnd(2, '0'))
}

/** 2745050 -> "27450.50" */
export function fromPaise(paise) {
  const whole = Math.floor(paise / 100)
  const frac = String(paise % 100).padStart(2, '0')
  return `${whole}.${frac}`
}

export function sumMoney(values) {
  return fromPaise(values.reduce((acc, v) => acc + (toPaise(v) ?? 0), 0))
}

/** "100000.00" -> "₹1,00,000.00" (Indian digit grouping). */
export function formatInr(value) {
  if (value === null || value === undefined || value === '') return '—'
  const paise = toPaise(value)
  if (paise === null) return String(value)
  return inr.format(paise / 100)
}

export function isValidAmount(value) {
  const paise = toPaise(value)
  return paise !== null && paise > 0 && paise <= 999999999999
}
