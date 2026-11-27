import { EmptyState, ErrorState, LoadingState } from './States.jsx'

/**
 * Dense, scannable table with server paging, optional row selection and per-row messages
 * (NFR-003). Below 640 px rows render as cards via CSS (data-label on each cell, RESP-03).
 *
 * columns: [{ key, header, render?(row), align?: 'right', sortable?: bool }]
 */
export function DataTable({
  caption,
  columns,
  rows,
  getRowId,
  loading,
  error,
  onRetry,
  emptyTitle = 'Nothing to show.',
  emptyContent,
  onRowClick,
  rowLabel,
  selectable = false,
  selected,
  onSelectionChange,
  rowStatus,
  sort,
  onSortChange,
  page,
  pageSize,
  total,
  onPageChange,
}) {
  if (error && !rows?.length) return <ErrorState error={error} onRetry={onRetry} />
  if (loading && !rows) return <LoadingState />
  if (!rows?.length) return <EmptyState title={emptyTitle}>{emptyContent}</EmptyState>

  const allIds = rows.map(getRowId)
  const allSelected = selectable && allIds.every((id) => selected?.has(id))

  function toggle(id) {
    const next = new Set(selected)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    onSelectionChange(next)
  }

  function toggleAll() {
    const next = new Set(selected)
    if (allSelected) allIds.forEach((id) => next.delete(id))
    else allIds.forEach((id) => next.add(id))
    onSelectionChange(next)
  }

  function headerSort(col) {
    if (!col.sortable || !onSortChange) return undefined
    if (sort === col.key) return 'ascending'
    if (sort === `-${col.key}`) return 'descending'
    return 'none'
  }

  return (
    <div className="table-wrap" aria-busy={loading ? 'true' : undefined}>
      <table className="data-table">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr>
            {selectable && (
              <th scope="col" className="col-check">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={toggleAll}
                  aria-label="Select all rows on this page"
                />
              </th>
            )}
            {columns.map((col) => {
              const ariaSort = headerSort(col)
              return (
                <th
                  key={col.key}
                  scope="col"
                  aria-sort={ariaSort}
                  className={col.align === 'right' ? 'num' : undefined}
                >
                  {ariaSort ? (
                    <button
                      type="button"
                      className="sort-button"
                      onClick={() => onSortChange(sort === col.key ? `-${col.key}` : col.key)}
                    >
                      {col.header}
                      <span aria-hidden="true">
                        {ariaSort === 'ascending' ? ' ▲' : ariaSort === 'descending' ? ' ▼' : ' ↕'}
                      </span>
                    </button>
                  ) : (
                    col.header
                  )}
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const id = getRowId(row)
            const status = rowStatus?.(row)
            const label = rowLabel ? rowLabel(row) : String(id)
            return (
              <tr
                key={id}
                className={`${onRowClick ? 'clickable' : ''} ${selected?.has(id) ? 'selected' : ''} ${
                  status ? 'has-status' : ''
                }`}
                onClick={onRowClick ? (e) => {
                  if (e.target.closest('input, button, a, label')) return
                  onRowClick(row)
                } : undefined}
              >
                {selectable && (
                  <td className="col-check" data-label="Select">
                    <input
                      type="checkbox"
                      checked={selected?.has(id) ?? false}
                      onChange={() => toggle(id)}
                      aria-label={`Select ${label}`}
                    />
                  </td>
                )}
                {columns.map((col, i) => (
                  <td key={col.key} data-label={col.header} className={col.align === 'right' ? 'num' : undefined}>
                    {i === 0 && onRowClick ? (
                      <button type="button" className="link-button row-link" onClick={() => onRowClick(row)}>
                        {col.render ? col.render(row) : row[col.key]}
                      </button>
                    ) : col.render ? (
                      col.render(row)
                    ) : (
                      row[col.key]
                    )}
                    {i === 0 && status && (
                      <span className="row-status" role="note">
                        {status}
                      </span>
                    )}
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
      {onPageChange && total !== undefined && (
        <Pagination page={page} pageSize={pageSize} total={total} onPageChange={onPageChange} />
      )}
    </div>
  )
}

export function Pagination({ page = 1, pageSize = 20, total = 0, onPageChange }) {
  const pages = Math.max(1, Math.ceil(total / pageSize))
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1
  const to = Math.min(page * pageSize, total)
  return (
    <nav className="pagination" aria-label="Pagination">
      <span className="muted">
        {from}–{to} of {total}
      </span>
      <button type="button" className="btn btn-sm" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
        Previous
      </button>
      <span aria-current="page">
        Page {page} of {pages}
      </span>
      <button type="button" className="btn btn-sm" disabled={page >= pages} onClick={() => onPageChange(page + 1)}>
        Next
      </button>
    </nav>
  )
}

/** Sticky bar for bulk actions (S-07, S-09). */
export function BulkActionBar({ children, label }) {
  return (
    <div className="bulk-bar" role="region" aria-label={label ?? 'Bulk actions'}>
      {children}
    </div>
  )
}
