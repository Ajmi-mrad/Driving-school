import { useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
} from '@/components/ui/pagination'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

export interface Column<T> {
  key: string
  header: ReactNode
  cell: (row: T) => ReactNode
  className?: string
}

export function DataTable<T extends { id: string }>({
  columns,
  rows,
  loading,
  emptyLabel,
  onRowClick,
  selectable,
  selectedIds,
  onSelectionChange,
  pageSize = 10,
}: {
  columns: Column<T>[]
  rows: T[]
  loading?: boolean
  emptyLabel?: string
  onRowClick?: (row: T) => void
  /** Enable a leading checkbox column with a header select-all. */
  selectable?: boolean
  selectedIds?: Set<string>
  onSelectionChange?: (ids: Set<string>) => void
  /** Rows per page for client-side pagination. `0` disables paging. */
  pageSize?: number
}) {
  const { t } = useTranslation()
  const selected = selectedIds ?? new Set<string>()
  const allSelected = rows.length > 0 && rows.every((r) => selected.has(r.id))

  // Client-side pagination. Selection/select-all stay scoped to the full
  // `rows` set (bulk actions act on everything); only the body is sliced.
  const [page, setPage] = useState(0)
  const paginated = pageSize > 0
  const pageCount = paginated ? Math.ceil(rows.length / pageSize) : 1
  // Reset to the first page whenever the (filtered) result set changes size.
  useEffect(() => setPage(0), [rows.length])
  const visibleRows = paginated
    ? rows.slice(page * pageSize, page * pageSize + pageSize)
    : rows

  const toggleAll = () => {
    if (!onSelectionChange) return
    onSelectionChange(allSelected ? new Set() : new Set(rows.map((r) => r.id)))
  }
  const toggleOne = (id: string) => {
    if (!onSelectionChange) return
    const next = new Set(selected)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    onSelectionChange(next)
  }

  const colCount = columns.length + (selectable ? 1 : 0)

  return (
    <div className="space-y-3">
    <div className="rounded-xl border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            {selectable && (
              <TableHead className="w-10">
                <input
                  type="checkbox"
                  aria-label={t('common.selectAll')}
                  className="size-4 cursor-pointer accent-primary align-middle"
                  checked={allSelected}
                  onChange={toggleAll}
                />
              </TableHead>
            )}
            {columns.map((col) => (
              <TableHead key={col.key} className={col.className}>
                {col.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {loading ? (
            Array.from({ length: 5 }).map((_, i) => (
              <TableRow key={i}>
                {Array.from({ length: colCount }).map((_, j) => (
                  <TableCell key={j}>
                    <Skeleton className="h-5 w-full max-w-32" />
                  </TableCell>
                ))}
              </TableRow>
            ))
          ) : rows.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={colCount}
                className="h-24 text-center text-muted-foreground"
              >
                {emptyLabel ?? t('common.noResults')}
              </TableCell>
            </TableRow>
          ) : (
            visibleRows.map((row) => (
              <TableRow
                key={row.id}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={cn(
                  onRowClick && 'cursor-pointer',
                  selected.has(row.id) && 'bg-primary/5',
                )}
              >
                {selectable && (
                  <TableCell className="w-10" onClick={(e) => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      aria-label={t('common.select')}
                      className="size-4 cursor-pointer accent-primary align-middle"
                      checked={selected.has(row.id)}
                      onChange={() => toggleOne(row.id)}
                    />
                  </TableCell>
                )}
                {columns.map((col) => (
                  <TableCell key={col.key} className={col.className}>
                    {col.cell(row)}
                  </TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>

    {!loading && pageCount > 1 && (
      <Pagination className="justify-end">
        <PaginationContent>
          <PaginationItem>
            <PaginationLink
              size="default"
              aria-label={t('common.previous')}
              aria-disabled={page === 0}
              className={cn('cursor-pointer gap-1', page === 0 && 'pointer-events-none opacity-50')}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="size-4 rtl:rotate-180" />
              <span className="hidden sm:block">{t('common.previous')}</span>
            </PaginationLink>
          </PaginationItem>
          <PaginationItem>
            <span className="px-3 text-sm text-muted-foreground">
              {t('common.pageOf', { page: page + 1, total: pageCount })}
            </span>
          </PaginationItem>
          <PaginationItem>
            <PaginationLink
              size="default"
              aria-label={t('common.next')}
              aria-disabled={page >= pageCount - 1}
              className={cn(
                'cursor-pointer gap-1',
                page >= pageCount - 1 && 'pointer-events-none opacity-50',
              )}
              onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
            >
              <span className="hidden sm:block">{t('common.next')}</span>
              <ChevronRight className="size-4 rtl:rotate-180" />
            </PaginationLink>
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    )}
    </div>
  )
}
