// Client-side validation mirroring the backend rules (UX only; the backend is the security boundary).
import { isValidAmount, toPaise } from './money.js'
import { nightsBetween, todayIst } from './dates.js'

/** Validates one line item (ClaimDtos.LineItemRequest + backbone §4.4). Returns { field: message }. */
export function validateLine(line, today = todayIst()) {
  const e = {}
  if (!line.category) e.category = 'Choose a category.'
  const amount = String(line.amount ?? '').trim()
  if (!amount) e.amount = 'Enter an amount.'
  else if (!/^\d+(\.\d{1,2})?$/.test(amount)) e.amount = 'Use a number with at most 2 decimals.'
  else if (!isValidAmount(amount)) e.amount = 'Amount must be greater than 0.'

  const vendor = (line.vendor ?? '').trim()
  if (!vendor) e.vendor = 'Enter the vendor.'
  else if (vendor.length > 120) e.vendor = 'Vendor must be 120 characters or fewer.'

  const receiptNumber = (line.receiptNumber ?? '').trim()
  if (!receiptNumber) e.receiptNumber = 'Enter the receipt number.'
  else if (receiptNumber.length > 60) e.receiptNumber = 'Receipt number must be 60 characters or fewer.'

  if (line.category === 'HOTEL') {
    if (!(line.city ?? '').trim()) e.city = 'Enter the city.'
    else if (line.city.trim().length > 100) e.city = 'City must be 100 characters or fewer.'
    if (!line.checkInDate) e.checkInDate = 'Enter the check-in date.'
    else if (line.checkInDate > today) e.checkInDate = 'Check-in cannot be in the future.'
    if (!line.checkOutDate) e.checkOutDate = 'Enter the check-out date.'
    else if (line.checkInDate && (nightsBetween(line.checkInDate, line.checkOutDate) ?? 0) < 1) {
      e.checkOutDate = 'Check-out must be after check-in.'
    }
  } else if (!line.expenseDate) {
    e.expenseDate = 'Enter the expense date.'
  } else if (line.expenseDate > today) {
    e.expenseDate = 'Expense date cannot be in the future.'
  }
  return e
}

/** True when a receipt is mandatory for this amount (POL-005: strictly above the limit). */
export function receiptRequired(amount, receiptMandatoryAbove) {
  const a = toPaise(amount)
  const limit = toPaise(receiptMandatoryAbove)
  if (a === null || limit === null) return false
  return a > limit
}

export function validateDelegation(form) {
  const e = {}
  if (!form.delegateId) e.delegateId = 'Choose a delegate.'
  if (!form.startDate) e.startDate = 'Enter the start date.'
  if (!form.endDate) e.endDate = 'Enter the end date.'
  else if (form.startDate && form.endDate < form.startDate) e.endDate = 'End date must be on or after the start date.'
  return e
}

export function validateQueueFilters(f) {
  const e = {}
  const min = f.minAmount ? toPaise(f.minAmount) : null
  const max = f.maxAmount ? toPaise(f.maxAmount) : null
  if (f.minAmount && min === null) e.minAmount = 'Enter a valid amount.'
  if (f.maxAmount && max === null) e.maxAmount = 'Enter a valid amount.'
  if (min !== null && max !== null && min > max) e.maxAmount = 'Maximum must be at least the minimum.'
  if (f.fromDate && f.toDate && f.fromDate > f.toDate) e.toDate = '"To" date must be on or after "From".'
  return e
}
