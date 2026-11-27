// The single HTTP client (frontend/SPEC.md §6.1). Every backend call goes through here.
// Base URL comes from env; in dev Vite proxies /api, in Docker nginx does.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export class ApiError extends Error {
  constructor(status, body, traceIdHeader) {
    // Backend errors use one shape: { code, message, details, traceId } (CLAUDE.md §11.4)
    super(body?.message ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code ?? (status === 0 ? 'NETWORK_ERROR' : 'INTERNAL_ERROR')
    this.details = body?.details ?? null
    this.traceId = body?.traceId ?? traceIdHeader ?? null
  }
}

let tokenProvider = () => null
let unauthorizedHandler = () => {}

/** Wired by AuthContext: the JWT lives in memory only (SPEC §6.2). */
export function configureClient({ getToken, onUnauthorized }) {
  tokenProvider = getToken
  unauthorizedHandler = onUnauthorized
}

export function buildQuery(params = {}) {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    q.set(k, String(v))
  }
  const s = q.toString()
  return s ? `?${s}` : ''
}

function fileNameFrom(disposition) {
  if (!disposition) return null
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star) return decodeURIComponent(star[1])
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : null
}

/**
 * @param {string} method
 * @param {string} path  path below /api/v1, e.g. "/claims"
 * @param {object} [opts] { body, query, idempotencyKey, signal, responseType: 'json'|'blob', auth }
 */
export async function request(method, path, opts = {}) {
  const { body, query, idempotencyKey, signal, responseType = 'json', auth = true } = opts
  const headers = { Accept: responseType === 'blob' ? '*/*' : 'application/json' }
  const token = auth ? tokenProvider() : null
  if (token) headers.Authorization = `Bearer ${token}`
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey

  let payload
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  let res
  try {
    res = await fetch(`${BASE_URL}${path}${buildQuery(query)}`, { method, headers, body: payload, signal })
  } catch (err) {
    if (err?.name === 'AbortError') throw err
    throw new ApiError(0, { message: 'Network error' })
  }

  const traceHeader = res.headers.get('X-Trace-Id')

  if (res.ok && responseType === 'blob') {
    const blob = await res.blob()
    return { blob, fileName: fileNameFrom(res.headers.get('Content-Disposition')) }
  }

  const text = res.status === 204 ? '' : await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      // Non-JSON body (e.g. proxy error page): keep the status, drop the body.
      data = null
    }
  }
  if (!res.ok) {
    const error = new ApiError(res.status, data, traceHeader)
    if (res.status === 401 && auth) unauthorizedHandler(error)
    throw error
  }
  return data
}

export const api = {
  get: (path, opts) => request('GET', path, opts),
  post: (path, body, opts) => request('POST', path, { ...opts, body }),
  put: (path, body, opts) => request('PUT', path, { ...opts, body }),
  delete: (path, opts) => request('DELETE', path, opts),
}

export function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
