import { useNavigate } from 'react-router-dom'
import { approvalsApi } from '../api/index.js'
import { DataTable } from '../components/DataTable.jsx'
import { DateDisplay, FlagList, MoneyDisplay, StatusBadge } from '../components/Display.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useQuery } from '../hooks/useQuery.js'
import { useUrlQuery } from '../hooks/useUrlQuery.js'
import { ageFrom } from '../utils/dates.js'

/** S-05 ApprovalsQueuePage (API-014): manager / finance-head steps waiting for the caller. FR-005, FR-008. */
export default function ApprovalsQueuePage() {
  useDocumentTitle('Approvals')
  const navigate = useNavigate()
  const [q, setQ] = useUrlQuery({ page: 1, pageSize: 20 })
  const { data, error, loading, reload } = useQuery(
    (opts) => approvalsApi.queue({ page: q.page, pageSize: q.pageSize }, opts),
    [q.page, q.pageSize],
  )

  const columns = [
    { key: 'claimNumber', header: 'Claim' },
    { key: 'claimant', header: 'Claimant', render: (r) => r.claimant?.name },
    { key: 'department', header: 'Department' },
    { key: 'status', header: 'Step', render: (r) => <StatusBadge status={r.status} /> },
    { key: 'total', header: 'Total', align: 'right', render: (r) => <MoneyDisplay value={r.total} /> },
    { key: 'flags', header: 'Flags', render: (r) => <FlagList flags={r.flags} /> },
    { key: 'submittedAt', header: 'Submitted', render: (r) => <DateDisplay value={r.submittedAt} /> },
    { key: 'pendingSince', header: 'Waiting', render: (r) => ageFrom(r.pendingSince) },
    {
      key: 'onBehalfOf',
      header: 'On behalf of',
      render: (r) => (r.onBehalfOf ? r.onBehalfOf.name : <span className="muted">—</span>),
    },
  ]

  return (
    <section>
      <div className="page-head">
        <h1>Approvals</h1>
        {data && <span className="muted">{data.total} waiting</span>}
      </div>
      <DataTable
        caption="Claims waiting for your approval"
        columns={columns}
        rows={data?.items}
        getRowId={(r) => r.claimId}
        rowLabel={(r) => r.claimNumber}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyTitle="Nothing is waiting for your approval."
        onRowClick={(r) => navigate(`/approvals/${r.claimId}`)}
        page={data?.page}
        pageSize={data?.pageSize}
        total={data?.total}
        onPageChange={(page) => setQ({ page }, { resetPage: false })}
      />
    </section>
  )
}
