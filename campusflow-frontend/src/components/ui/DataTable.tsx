import type { ReactNode } from 'react';
export interface Column<T> {
  key: string;
  label: string;
  render: (row: T) => ReactNode;
}
export function DataTable<T extends { id: number }>({
  rows,
  columns,
  caption,
}: {
  rows: T[];
  columns: Column<T>[];
  caption: string;
}) {
  return (
    <div
      className="table-scroll"
      tabIndex={0}
      role="region"
      aria-label={`${caption} table; scroll horizontally if needed`}
    >
      <table>
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {columns.map((column) => (
              <th scope="col" key={column.key}>
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              {columns.map((column) => (
                <td key={column.key} data-label={column.label}>
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
