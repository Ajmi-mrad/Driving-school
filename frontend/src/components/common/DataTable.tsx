import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
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
}) {
  const { t } = useTranslation()
  const selected = selectedIds ?? new Set<string>()
  const allSelected = rows.length > 0 && rows.every((r) => selected.has(r.id))

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
            rows.map((row) => (
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
  )
}
