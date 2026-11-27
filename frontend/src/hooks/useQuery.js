import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

/**
 * Loads data with `fn({ signal })`. Re-runs when `deps` change; aborts on unmount.
 * Returns { data, error, loading, reload }.
 */
export function useQuery(fn, deps, { enabled = true } = {}) {
  const [state, setState] = useState({ data: null, error: null, loading: enabled })
  const [tick, setTick] = useState(0)
  const fnRef = useRef(fn)
  useLayoutEffect(() => {
    fnRef.current = fn
  })

  // Fetching is synchronising with an external system, so setting loading state here is intended.
  useEffect(() => {
    if (!enabled) {
      // oxlint-disable-next-line react/set-state-in-effect
      setState((s) => ({ ...s, loading: false }))
      return undefined
    }
    const controller = new AbortController()
    // oxlint-disable-next-line react/set-state-in-effect
    setState((s) => ({ ...s, loading: true, error: null }))
    fnRef
      .current({ signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setState({ data, error: null, loading: false })
      })
      .catch((error) => {
        if (error?.name === 'AbortError' || controller.signal.aborted) return
        setState((s) => ({ data: s.data, error, loading: false }))
      })
    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick, enabled])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { ...state, reload }
}
