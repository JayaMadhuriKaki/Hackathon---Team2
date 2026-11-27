// Error code -> user message (frontend/SPEC.md §3.1). Never show raw bodies or stack traces.

const MESSAGES = {
  VALIDATION_FAILED: 'Please fix the highlighted fields.',
  UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
  FORBIDDEN: 'You do not have permission to do this.',
  NOT_CURRENT_APPROVER: 'This claim is not waiting for your decision any more.',
  NOT_FOUND: 'Not found or you do not have access.',
  METHOD_NOT_ALLOWED: 'Something went wrong. Please try again.',
  STATE_CONFLICT: 'This item has changed since you opened it. The page has been refreshed.',
  FILE_TOO_LARGE: 'The file is too large. Maximum size is 5 MB.',
  UNSUPPORTED_FILE_TYPE: 'This file type is not accepted. Use PDF, JPG or PNG.',
  RECEIPT_REQUIRED: 'A receipt is required for one or more lines.',
  CLAIM_HAS_NO_LINES: 'Add at least one expense line before submitting.',
  COMMENT_REQUIRED: 'Please add a comment.',
  SELF_APPROVAL_FORBIDDEN: 'You cannot act on your own claim.',
  INVALID_DELEGATE: 'Choose a finance auditor or finance head other than yourself.',
  DELEGATION_OVERLAP: 'You already have a delegation for these dates. Cancel it first or pick other dates.',
  CLAIM_NOT_ELIGIBLE_FOR_PAYOUT:
    'One or more claims are no longer eligible for payout. The list has been refreshed.',
  IDEMPOTENCY_KEY_REUSED: 'Please try again.',
  RATE_LIMITED: 'Too many attempts. Please wait a moment and try again.',
  INTERNAL_ERROR: 'Something went wrong. Please try again.',
}

/** Codes where the server message is specific and safe to show (business-rule text). */
const USE_SERVER_MESSAGE = new Set(['BUSINESS_RULE_VIOLATION'])

export function messageFor(error) {
  if (!error) return ''
  const code = error.code
  if (USE_SERVER_MESSAGE.has(code)) {
    return error.message || 'This action breaks a business rule.'
  }
  if (code && MESSAGES[code]) return MESSAGES[code]
  if (error.status === 404) return MESSAGES.NOT_FOUND
  if (error.status === 403) return MESSAGES.FORBIDDEN
  return MESSAGES.INTERNAL_ERROR
}

/** Field errors from a 400 VALIDATION_FAILED body: details.fields -> { field: message }. */
export function fieldErrors(error) {
  const fields = error?.details?.fields
  if (!fields) return {}
  if (Array.isArray(fields)) {
    return Object.fromEntries(fields.map((f) => [f.field, f.message]))
  }
  return { ...fields }
}
