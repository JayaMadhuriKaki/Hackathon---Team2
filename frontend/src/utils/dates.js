// Date helpers. Timestamps are UTC from the API and shown in IST (ASM-002).
// Date-only values (YYYY-MM-DD) are shown as-is, never shifted.

const TZ = 'Asia/Kolkata'

const dateTimeFmt = new Intl.DateTimeFormat('en-GB', {
  timeZone: TZ,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

const dateOnlyFmt = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'UTC',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

/** "2026-11-27T18:45:00Z" -> "28 Nov 2026, 00:15 IST" */
export function formatTimestamp(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  return `${dateTimeFmt.format(d)} IST`
}

/** "2026-11-27" -> "27 Nov 2026" */
export function formatDate(ymd) {
  if (!ymd) return '—'
  const d = new Date(`${ymd}T00:00:00Z`)
  if (Number.isNaN(d.getTime())) return String(ymd)
  return dateOnlyFmt.format(d)
}

/** Today's date in IST as YYYY-MM-DD (used for "not in the future" checks). */
export function todayIst(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now)
  return parts
}

/** Nights between two YYYY-MM-DD dates (ASM-005). Returns null if invalid. */
export function nightsBetween(checkIn, checkOut) {
  if (!checkIn || !checkOut) return null
  const a = Date.parse(`${checkIn}T00:00:00Z`)
  const b = Date.parse(`${checkOut}T00:00:00Z`)
  if (Number.isNaN(a) || Number.isNaN(b)) return null
  return Math.round((b - a) / 86400000)
}

/** Human "waiting for" duration from a timestamp to now. */
export function ageFrom(iso, now = new Date()) {
  if (!iso) return '—'
  const ms = now.getTime() - new Date(iso).getTime()
  if (Number.isNaN(ms) || ms < 0) return '—'
  const h = Math.floor(ms / 3600000)
  if (h < 1) return `${Math.max(1, Math.floor(ms / 60000))} min`
  if (h < 48) return `${h} h`
  return `${Math.floor(h / 24)} d`
}
