import { useMemo, useState } from 'react'
import { dashboardApi, departmentsApi } from '../api/index.js'
import { CATEGORY_LABEL, MoneyDisplay } from '../components/Display.jsx'
import { FormField } from '../components/FormField.jsx'
import { EmptyState, ErrorState, LoadingState } from '../components/States.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'
import { useQuery } from '../hooks/useQuery.js'
import { useUrlQuery } from '../hooks/useUrlQuery.js'
import { toPaise } from '../utils/money.js'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** API-032 averages are decimal strings, or null when no claim matches (contract ApprovalTimeReport). */
function hours(value) {
  return value === null || value === undefined ? '—' : `${value} h`
}

function monthLabel(key) {
  const m = /^(\d{4})-(\d{2})$/.exec(key ?? '')
  return m ? `${MONTHS[Number(m[2]) - 1]} ${m[1]}` : key
}

function Section({ title, query, children, footnote }) {
  return (
    <section className="card dash-card" aria-labelledby={`dash-${title}`}>
      <h2 id={`dash-${title}`}>{title}</h2>
      {query.loading && !query.data ? (
        <LoadingState />
      ) : query.error ? (
        <ErrorState error={query.error} onRetry={query.reload} />
      ) : (
        children(query.data)
      )}
      {footnote && <p className="hint">{footnote}</p>}
    </section>
  )
}

/** Table with an inline CSS bar column (no chart library approved — SPEC S-11). */
function SpendTable({ report, labelFor, caption }) {
  if (!report?.items?.length) return <EmptyState title="No spend in this period." />
  const max = Math.max(...report.items.map((i) => toPaise(i.total) ?? 0), 1)
  return (
    <table className="data-table bar-table">
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          <th scope="col">Group</th>
          <th scope="col" className="num">
            Total
          </th>
          <th scope="col" className="num">
            Lines
          </th>
          <th scope="col" aria-hidden="true" className="bar-col" />
        </tr>
      </thead>
      <tbody>
        {report.items.map((i) => (
          <tr key={i.key}>
            <th scope="row">{labelFor(i.key)}</th>
            <td className="num">
              <MoneyDisplay value={i.total} />
            </td>
            <td className="num">{i.lineCount}</td>
            <td aria-hidden="true" className="bar-col">
              <span className="bar" style={{ width: `${Math.round(((toPaise(i.total) ?? 0) / max) * 100)}%` }} />
            </td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <th scope="row">Total</th>
          <td className="num">
            <MoneyDisplay value={report.grandTotal} />
          </td>
          <td />
          <td aria-hidden="true" />
        </tr>
      </tfoot>
    </table>
  )
}

/** S-11 DashboardPage (API-030..032, API-036): FR-015, FR-016, FR-017, ASM-010. Aggregates only. */
export default function DashboardPage() {
  useDocumentTitle('Dashboard')
  const [q, setQ] = useUrlQuery({})
  const [draft, setDraft] = useState({ fromDate: q.fromDate ?? '', toDate: q.toDate ?? '', departmentId: q.departmentId ?? '' })
  const [dateError, setDateError] = useState(null)
  const filter = useMemo(
    () => ({ fromDate: q.fromDate, toDate: q.toDate, departmentId: q.departmentId }),
    [q.fromDate, q.toDate, q.departmentId],
  )
  const key = JSON.stringify(filter)

  const deptQ = useQuery((opts) => departmentsApi.list(opts), [])
  const byCategory = useQuery((opts) => dashboardApi.spend({ ...filter, groupBy: 'category' }, opts), [key])
  const byDept = useQuery((opts) => dashboardApi.spend({ ...filter, groupBy: 'department' }, opts), [key])
  const byMonth = useQuery((opts) => dashboardApi.spend({ ...filter, groupBy: 'month' }, opts), [key])
  const violations = useQuery((opts) => dashboardApi.violations(filter, opts), [key])
  const approvalTime = useQuery((opts) => dashboardApi.approvalTime(filter, opts), [key])

  const deptName = (id) => deptQ.data?.find((d) => d.departmentId === id)?.name ?? id

  function apply(e) {
    e.preventDefault()
    if (draft.fromDate && draft.toDate && draft.fromDate > draft.toDate) {
      setDateError('"To" date must be on or after "From".')
      return
    }
    setDateError(null)
    setQ(draft)
  }

  return (
    <section>
      <h1>Dashboard</h1>
      <form className="filter-bar" onSubmit={apply} aria-label="Dashboard filters">
        <FormField label="From (expense date)">
          <input type="date" value={draft.fromDate} onChange={(e) => setDraft({ ...draft, fromDate: e.target.value })} />
        </FormField>
        <FormField label="To" error={dateError}>
          <input type="date" value={draft.toDate} onChange={(e) => setDraft({ ...draft, toDate: e.target.value })} />
        </FormField>
        <FormField label="Department">
          <select value={draft.departmentId} onChange={(e) => setDraft({ ...draft, departmentId: e.target.value })}>
            <option value="">All</option>
            {(deptQ.data ?? []).map((d) => (
              <option key={d.departmentId} value={d.departmentId}>
                {d.name}
              </option>
            ))}
          </select>
        </FormField>
        <div className="filter-actions">
          <button type="submit" className="btn btn-primary">
            Apply
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => {
              setDraft({ fromDate: '', toDate: '', departmentId: '' })
              setQ({ fromDate: '', toDate: '', departmentId: '' })
            }}
          >
            Clear
          </button>
        </div>
      </form>

      <div className="dash-grid">
        <Section title="Average approval time" query={approvalTime} footnote="Submit to final approval, calendar time.">
          {(d) =>
            !d?.approvedClaims ? (
              <EmptyState title="No approved claims in this period." />
            ) : (
              <div className="kpis">
                <div className="kpi">
                  <span className="kpi-value">{hours(d.averageHours)}</span>
                  <span className="kpi-label">All approved claims ({d.approvedClaims})</span>
                </div>
                <div className="kpi">
                  <span className="kpi-value">{hours(d.averageHoursRouted)}</span>
                  <span className="kpi-label">Through the approval chain ({d.routedClaims})</span>
                </div>
              </div>
            )
          }
        </Section>

        <Section title="Policy violations" query={violations}>
          {(d) =>
            !d?.items?.length ? (
              <EmptyState title="No policy violations in this period." />
            ) : (
              <table className="data-table">
                <caption className="sr-only">Policy violations by rule</caption>
                <thead>
                  <tr>
                    <th scope="col">Rule</th>
                    <th scope="col">Type</th>
                    <th scope="col" className="num">
                      Lines
                    </th>
                    <th scope="col" className="num">
                      Claims
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {d.items.map((v) => (
                    <tr key={`${v.ruleId}-${v.resultType}`}>
                      <th scope="row">{v.ruleId}</th>
                      <td>{v.resultType.replaceAll('_', ' ').toLowerCase()}</td>
                      <td className="num">{v.count}</td>
                      <td className="num">{v.claimCount}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <th scope="row">Total</th>
                    <td />
                    <td className="num">{d.total}</td>
                    <td />
                  </tr>
                </tfoot>
              </table>
            )
          }
        </Section>

        <Section
          title="Spend by category"
          query={byCategory}
          footnote="Approved, in-batch and paid claims; month of expense date."
        >
          {(d) => <SpendTable report={d} caption="Spend by category" labelFor={(k) => CATEGORY_LABEL[k] ?? k} />}
        </Section>

        <Section title="Spend by department" query={byDept}>
          {(d) => <SpendTable report={d} caption="Spend by department" labelFor={(k) => deptName(k)} />}
        </Section>

        <Section title="Spend by month" query={byMonth}>
          {(d) => <SpendTable report={d} caption="Spend by month" labelFor={monthLabel} />}
        </Section>
      </div>
    </section>
  )
}
