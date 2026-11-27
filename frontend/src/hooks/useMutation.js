import { useCallback, useRef, useState } from 'react'
import { newIdempotencyKey } from '../api/client.js'

/**
 * Runs a write action. Ignores calls while one is in flight (duplicate-submit guard, CLAUDE.md §10.5).
 * `fn(args, idempotencyKey)`; the same key is reused on retry after a network error for the same
 * args, and a fresh key is created after success or when the args change (SPEC §6.4).
 */
export function useMutation(fn) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)
  const inFlight = useRef(false)
  const keyRef = useRef({ key: null, args: null })

  const run = useCallback(
    async (...args) => {
      if (inFlight.current) return undefined
      inFlight.current = true
      setPending(true)
      setError(null)
      const argsJson = JSON.stringify(args)
      if (keyRef.current.args !== argsJson || !keyRef.current.key) {
        keyRef.current = { key: newIdempotencyKey(), args: argsJson }
      }
      try {
        const result = await fn(...args, keyRef.current.key)
        keyRef.current = { key: null, args: null }
        return result
      } catch (err) {
        // Keep the key only for network errors (status 0), so a retry is safe.
        if (err?.status !== 0) keyRef.current = { key: null, args: null }
        setError(err)
        throw err
      } finally {
        inFlight.current = false
        setPending(false)
      }
    },
    [fn],
  )

  return { run, pending, error, reset: () => setError(null) }
}
