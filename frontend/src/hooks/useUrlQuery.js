import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

/** Page, page size and filters kept in the URL query (SPEC §3 "Lists"). */
export function useUrlQuery(defaults = {}) {
  const [params, setParams] = useSearchParams()

  const values = useMemo(() => {
    const v = { ...defaults }
    for (const [k, val] of params.entries()) v[k] = val
    if (v.page !== undefined) v.page = Number(v.page) || 1
    if (v.pageSize !== undefined) v.pageSize = Number(v.pageSize) || 20
    return v
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params])

  const update = useCallback(
    (patch, { resetPage = true } = {}) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          for (const [k, val] of Object.entries(patch)) {
            if (val === undefined || val === null || val === '') next.delete(k)
            else next.set(k, String(val))
          }
          if (resetPage && !('page' in patch)) next.delete('page')
          return next
        },
        { replace: true },
      )
    },
    [setParams],
  )

  return [values, update]
}
