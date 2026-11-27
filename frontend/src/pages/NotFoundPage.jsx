import { Link } from 'react-router-dom'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'

export default function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <section>
      <h1>Page not found</h1>
      <p>The page you asked for does not exist.</p>
      <Link to="/claims">Go to my claims</Link>
    </section>
  )
}
