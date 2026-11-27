import { describe, expect, it, vi } from 'vitest'
import { ApiError, configureClient, request } from '../../../frontend/src/api/client.js'
import { json, mockFetch } from './helpers.jsx'

describe('API client (TC-FE-030..035)', () => {
  it('parses the standard error shape into ApiError', async () => {
    mockFetch({ 'GET /x': json({ code: 'STATE_CONFLICT', message: 'm', details: { a: 1 }, traceId: 't-1' }, 409) })
    const err = await request('GET', '/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ status: 409, code: 'STATE_CONFLICT', details: { a: 1 }, traceId: 't-1' })
  })

  it('handles a non-JSON error body and takes the trace ID from X-Trace-Id', async () => {
    mockFetch({
      'GET /x': new Response('<html>Bad gateway</html>', { status: 502, headers: { 'X-Trace-Id': 'hdr-9' } }),
    })
    const err = await request('GET', '/x').catch((e) => e)
    expect(err.code).toBe('INTERNAL_ERROR')
    expect(err.traceId).toBe('hdr-9')
  })

  it('maps network failures to status 0', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    const err = await request('GET', '/x').catch((e) => e)
    expect(err.status).toBe(0)
  })

  it('sends the bearer token and the Idempotency-Key header', async () => {
    configureClient({ getToken: () => 'tok', onUnauthorized: () => {} })
    const f = mockFetch({ 'POST /claims/1/submit': json({ ok: true }) })
    await request('POST', '/claims/1/submit', { idempotencyKey: 'key-1' })
    const headers = f.mock.calls[0][1].headers
    expect(headers.Authorization).toBe('Bearer tok')
    expect(headers['Idempotency-Key']).toBe('key-1')
    configureClient({ getToken: () => null, onUnauthorized: () => {} })
  })

  it('calls the unauthorized handler on 401', async () => {
    const onUnauthorized = vi.fn()
    configureClient({ getToken: () => 'tok', onUnauthorized })
    mockFetch({ 'GET /me': json({ code: 'UNAUTHENTICATED', message: 'no' }, 401) })
    await request('GET', '/me').catch(() => {})
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
    configureClient({ getToken: () => null, onUnauthorized: () => {} })
  })

  it('builds query strings and skips empty values', async () => {
    const f = mockFetch({ 'GET /review-queue': json({ items: [] }) })
    await request('GET', '/review-queue', { query: { page: 2, flag: '', category: 'MEALS', x: null } })
    expect(f.mock.calls[0][0]).toBe('/api/v1/review-queue?page=2&category=MEALS')
  })

  it('returns a blob and file name for downloads', async () => {
    mockFetch({
      'GET /payout-batches/b1/file': new Response('a,b', {
        status: 200,
        headers: { 'Content-Disposition': 'attachment; filename="EXPENSEFLOW_PAYOUT_PB-2026-0001_20261127.csv"' },
      }),
    })
    const res = await request('GET', '/payout-batches/b1/file', { responseType: 'blob' })
    expect(res.fileName).toBe('EXPENSEFLOW_PAYOUT_PB-2026-0001_20261127.csv')
    expect(await res.blob.text()).toBe('a,b')
  })
})
