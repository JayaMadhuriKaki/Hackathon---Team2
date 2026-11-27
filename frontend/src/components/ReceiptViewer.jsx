import { useEffect, useState } from 'react'
import { claimsApi } from '../api/index.js'
import { ErrorState, LoadingState } from './States.jsx'

/**
 * Loads a receipt (API-011) as a blob and shows it: image with zoom/rotate, or PDF in <object>.
 * Object URL revoked on change/unmount (SPEC §5).
 */
export function ReceiptViewer({ claimId, line }) {
  const [state, setState] = useState({ url: null, type: null, loading: false, error: null })
  const [zoom, setZoom] = useState(1)
  const [rotate, setRotate] = useState(0)
  const hasReceipt = Boolean(line?.receipt)
  const lineItemId = line?.lineItemId

  // Loading the blob is synchronising with an external system, so resetting state here is intended.
  useEffect(() => {
    if (!hasReceipt) {
      // oxlint-disable-next-line react/set-state-in-effect
      setState({ url: null, type: null, loading: false, error: null })
      return undefined
    }
    const controller = new AbortController()
    let url = null
    // oxlint-disable-next-line react/set-state-in-effect
    setState({ url: null, type: null, loading: true, error: null })
    setZoom(1)
    setRotate(0)
    claimsApi
      .downloadReceipt(claimId, lineItemId, { signal: controller.signal })
      .then(({ blob }) => {
        url = URL.createObjectURL(blob)
        setState({ url, type: blob.type, loading: false, error: null })
      })
      .catch((error) => {
        if (error?.name !== 'AbortError') setState({ url: null, type: null, loading: false, error })
      })
    return () => {
      controller.abort()
      if (url) URL.revokeObjectURL(url)
    }
  }, [claimId, lineItemId, hasReceipt])

  if (!line) return <div className="receipt-empty">Select a line to see its receipt.</div>
  if (!hasReceipt) return <div className="receipt-empty">Line {line.lineNo} has no receipt.</div>
  if (state.loading) return <LoadingState label="Loading receipt…" />
  if (state.error) return <ErrorState error={state.error} />
  if (!state.url) return null

  const isPdf = state.type === 'application/pdf'
  return (
    <figure className="receipt">
      <div className="receipt-tools">
        {!isPdf && (
          <>
            <button type="button" className="btn btn-sm" onClick={() => setZoom((z) => Math.min(3, z + 0.25))}>
              Zoom in
            </button>
            <button type="button" className="btn btn-sm" onClick={() => setZoom((z) => Math.max(0.5, z - 0.25))}>
              Zoom out
            </button>
            <button type="button" className="btn btn-sm" onClick={() => setRotate((r) => (r + 90) % 360)}>
              Rotate
            </button>
          </>
        )}
        <a className="btn btn-sm" href={state.url} target="_blank" rel="noopener noreferrer">
          Open in new tab
        </a>
      </div>
      <div className="receipt-frame">
        {isPdf ? (
          <object data={state.url} type="application/pdf" aria-label={`Receipt for line ${line.lineNo}`}>
            <p>PDF preview is not available. Use “Open in new tab”.</p>
          </object>
        ) : (
          <img
            src={state.url}
            alt={`Receipt for line ${line.lineNo} (${line.vendor})`}
            style={{ transform: `scale(${zoom}) rotate(${rotate}deg)` }}
          />
        )}
      </div>
      <figcaption className="muted">
        Line {line.lineNo} · receipt no. {line.receiptNumber}
      </figcaption>
    </figure>
  )
}
