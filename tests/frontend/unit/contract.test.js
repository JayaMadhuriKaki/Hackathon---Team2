// Contract test (CLAUDE.md §12.5): the frontend API layer must match architecture/api-contracts.yaml.
// Parses the contract text (no YAML dependency) for paths, methods and enums.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it, vi } from 'vitest'
import * as apis from '../../../frontend/src/api/index.js'
import { statusLabel } from '../../../frontend/src/components/Display.jsx'
import { messageFor } from '../../../frontend/src/utils/errors.js'

// Normalise CRLF (Windows checkouts) so the line-based parsing below works everywhere.
const CONTRACT = readFileSync(path.resolve(__dirname, '../../../architecture/api-contracts.yaml'), 'utf8').replace(
  /\r\n/g,
  '\n',
)

/** [{ method, path, regex }] from the `paths:` section. */
function contractOperations() {
  const lines = CONTRACT.split(/\r?\n/)
  const ops = []
  let inPaths = false
  let current = null
  for (const line of lines) {
    if (/^paths:/.test(line)) inPaths = true
    else if (/^\S/.test(line)) inPaths = false
    if (!inPaths) continue
    const p = /^ {2}(\/\S*):\s*$/.exec(line)
    if (p) current = p[1]
    const m = /^ {4}(get|post|put|delete|patch):\s*$/.exec(line)
    if (m && current) {
      const regex = new RegExp(`^${current.replace(/\{[^}]+\}/g, '[^/]+')}$`)
      ops.push({ method: m[1].toUpperCase(), path: current, regex })
    }
  }
  return ops
}

/** Values of `enum: [A, B]` (inline) or a block list under components.schemas.<name>. */
function schemaEnum(name) {
  const block = new RegExp(`\\n {4}${name}:\\n([\\s\\S]*?)(?=\\n {4}\\S|\\n\\S)`).exec(CONTRACT)?.[1] ?? ''
  const inline = /enum:\s*\[([^\]]+)\]/.exec(block)
  if (inline) return inline[1].split(',').map((s) => s.trim())
  return [...block.matchAll(/^ {8}- (\S+)$/gm)].map((m) => m[1])
}

/** Calls every function in src/api/index.js and records METHOD + path. */
async function frontendCalls() {
  const seen = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init = {}) => {
      seen.push({ method: init.method ?? 'GET', path: new URL(url, 'http://x').pathname.replace(/^\/api\/v1/, '') })
      return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
    }),
  )
  const file = new File(['x'], 'r.png', { type: 'image/png' })
  for (const [groupName, group] of Object.entries(apis)) {
    for (const [fnName, fn] of Object.entries(group)) {
      const args = fnName === 'uploadReceipt' ? ['c1', 'l1', file] : ['id-1', 'id-2', {}]
      await fn(...args).catch(() => {})
      seen.at(-1).name = `${groupName}.${fnName}`
    }
  }
  return seen
}

describe('frontend ↔ api-contracts.yaml (TC-FE-130..133)', () => {
  const ops = contractOperations()

  it('the contract defines the 38 documented operations', () => {
    expect(ops).toHaveLength(38)
  })

  it('every frontend API call is a documented operation (method + path)', async () => {
    const calls = await frontendCalls()
    const undocumented = calls.filter((c) => !ops.some((o) => o.method === c.method && o.regex.test(c.path)))
    expect(undocumented).toEqual([])
  })

  it('every documented operation except health is used by the frontend', async () => {
    const calls = await frontendCalls()
    const unused = ops
      .filter((o) => o.path !== '/health')
      .filter((o) => !calls.some((c) => c.method === o.method && o.regex.test(c.path)))
      .map((o) => `${o.method} ${o.path}`)
    expect(unused).toEqual([])
  })

  it('status badges have a label for every claim, step and batch status', () => {
    const fallback = (s) => s.replaceAll('_', ' ').toLowerCase()
    for (const schema of ['ClaimStatus', 'StepStatus', 'PayoutBatchStatus']) {
      const values = schemaEnum(schema)
      expect(values.length, schema).toBeGreaterThan(0)
      for (const v of values) expect(statusLabel(v), `${schema}.${v}`).not.toBe(fallback(v))
    }
  })

  it('every error code maps to a specific message (never the generic fallback by accident)', () => {
    const codes = schemaEnum('ErrorCode')
    expect(codes).toHaveLength(20)
    const generic = 'Something went wrong. Please try again.'
    const allowedGeneric = new Set(['METHOD_NOT_ALLOWED', 'INTERNAL_ERROR'])
    for (const code of codes) {
      const msg = messageFor({ code, message: 'server text' })
      if (allowedGeneric.has(code)) expect(msg).toBe(generic)
      else expect(msg, code).not.toBe(generic)
    }
  })

  it('allowedActions values used by the UI are the contract enum', () => {
    expect(schemaEnum('AllowedAction')).toEqual(['EDIT', 'SUBMIT', 'APPROVE', 'REJECT', 'SEND_BACK', 'COMMENT'])
  })
})
