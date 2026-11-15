import type { ReactNode } from 'react';

export interface TableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  className?: string;
}

interface TableProps<T> {
  columns: TableColumn<T>[];
  rows: T[];
  getRowKey: (row: T) => string;
  emptyMessage?: string;
  caption: string;
}

export function Table<T>({
  columns,
  rows,
  getRowKey,
  emptyMessage = 'Chưa có dữ liệu.',
  caption,
}: TableProps<T>) {
  return (
    <div className="w-full overflow-x-auto rounded-control border border-border">
      <table className="w-full min-w-[560px] border-collapse text-left text-body">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-surface-muted">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={`h-11 border-b border-border px-4 font-semibold ${column.className ?? ''}`}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length > 0 ? (
            rows.map((row) => (
              <tr key={getRowKey(row)} className="border-b border-border last:border-b-0">
                {columns.map((column) => (
                  <td key={column.key} className={`h-11 px-4 py-2 ${column.className ?? ''}`}>
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-text-secondary">
                {emptyMessage}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
