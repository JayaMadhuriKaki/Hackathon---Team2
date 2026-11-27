import { useEffect, useState } from 'react'
import { getHealth } from '../api/health.js'

export default function HomePage() {
  const [health, setHealth] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getHealth().then(setHealth).catch((e) => setError(e.message))
  }, [])

  return (
    <section>
      <h1>Welcome</h1>
      <p>Frontend, backend and database are wired together.</p>
      <div className="card">
        <h2>Backend status</h2>
        {error && <p className="status status-down">Unreachable: {error}</p>}
        {!error && !health && <p>Checking…</p>}
        {health && (
          <p className="status status-up">
            {health.status} · database {health.database} · {health.timestamp}
          </p>
        )}
      </div>
    </section>
  )
}
