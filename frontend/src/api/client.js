// Thin fetch wrapper. All backend calls go through /api, which is proxied
// to the backend by Vite (dev) or nginx (Docker).
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export class ApiError extends Error {
  constructor(status, body) {
    // Backend errors use one shape: { code, message, details, traceId } (CLAUDE.md §11.4)
    super(body?.message ?? `Request failed with status ${status}`)
    this.code = body?.code
    this.details = body?.details
    this.traceId = body?.traceId
    this.status = status
    this.body = body
  }
}

async function request(method, path, body) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      // Non-JSON body (e.g. a proxy error page): keep the HTTP status, drop the body
      data = null
    }
  }
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  patch: (path, body) => request('PATCH', path, body),
  delete: (path) => request('DELETE', path),
}
