import { Link, useNavigate } from 'react-router-dom'
import { claimsApi } from '../api/index.js'
import { DataTable } from '../components/DataTable.jsx'
import { DateDisplay, MoneyDisplay, StatusBadge } from '../components/Display.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useQuery } from '../hooks/useQuery.js'
import { useUrlQuery } from '../hooks/useUrlQuery.js'

const EDITABLE = new Set(['DRAFT', 'SENT_BACK'])

/** S-02 MyClaimsPage — the caller's own claims only (API-004, FR-019, CL-15). */
export default function MyClaimsPage() {
  useDocumentTitle('My claims')
  const navigate = useNavigate()
  const [q, setQ] = useUrlQuery({ page: 1, pageSize: 20, sort: '-createdAt' })
  const { data, error, loading, reload } = useQuery(
    (opts) => claimsApi.listOwn({ page: q.page, pageSize: q.pageSize, sort: q.sort }, opts),
    [q.page, q.pageSize, q.sort],
  )

  const columns = [
    { key: 'claimNumber', header: 'Claim' },
    { key: 'createdAt', header: 'Created', sortable: true, render: (r) => <DateDisplay value={r.createdAt} /> },
    { key: 'submittedAt', header: 'Submitted', sortable: true, render: (r) => <DateDisplay value={r.submittedAt} /> },
    { key: 'lineCount', header: 'Lines', align: 'right' },
    { key: 'total', header: 'Total', align: 'right', render: (r) => <MoneyDisplay value={r.total} /> },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      render: (r) =>
        EDITABLE.has(r.status) ? (
          <Link to={`/claims/${r.claimId}/edit`} aria-label={`Edit ${r.claimNumber}`}>
            Edit
          </Link>
        ) : null,
    },
  ]

  return (
    <section>
      <div className="page-head">
        <h1>My claims</h1>
        <Link to="/claims/new" className="btn btn-primary">
          New claim
        </Link>
      </div>
      <DataTable
        caption="My claims"
        columns={columns}
        rows={data?.items}
        getRowId={(r) => r.claimId}
        rowLabel={(r) => r.claimNumber}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyTitle="You have no claims yet."
        emptyContent={
          <Link to="/claims/new" className="btn btn-primary">
            New claim
          </Link>
        }
        onRowClick={(r) => navigate(`/claims/${r.claimId}`)}
        sort={q.sort}
        onSortChange={(sort) => setQ({ sort })}
        page={data?.page}
        pageSize={data?.pageSize}
        total={data?.total}
        onPageChange={(page) => setQ({ page }, { resetPage: false })}
      />
    </section>
  )
}
