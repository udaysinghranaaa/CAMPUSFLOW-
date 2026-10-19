import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../services/apiClient';
import type { DirectoryEntry, Page } from '../../types/api';
import { DataTable } from '../../components/ui/DataTable';
import {
  EmptyState,
  ErrorState,
  LoadingSkeleton,
  PageHeader,
  Pagination,
  StatusBadge,
} from '../../components/ui/feedback';
export function DirectoryPage() {
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ['directory', page],
    queryFn: ({ signal }) =>
      api.request<Page<DirectoryEntry>>(`/api/v1/directory/faculty?page=${page}&size=12`, {
        signal,
      }),
  });
  return (
    <>
      <PageHeader
        eyebrow="PEOPLE OF YOUR CAMPUS"
        title="Faculty directory"
        description="Meet your academic community. Only permitted directory information appears here."
      />
      <section className="panel data-panel">
        {query.isPending ? (
          <LoadingSkeleton label="Loading the directory…" />
        ) : query.isError ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : (
          <>
            {query.data.content.length === 0 ? (
              <EmptyState
                title="The directory is empty"
                description="Faculty members will appear when profiles are added."
              />
            ) : (
              <DataTable
                rows={query.data.content}
                caption="Faculty directory"
                columns={[
                  {
                    key: 'name',
                    label: 'Faculty member',
                    render: (row) => <strong>{row.fullName}</strong>,
                  },
                  {
                    key: 'designation',
                    label: 'Designation',
                    render: (row) => row.designation ?? '—',
                  },
                  { key: 'department', label: 'Department', render: (row) => row.departmentCode },
                  {
                    key: 'status',
                    label: 'Status',
                    render: (row) => <StatusBadge value={row.active} />,
                  },
                ]}
              />
            )}
            <Pagination
              page={page}
              totalPages={query.data.totalPages}
              totalElements={query.data.totalElements}
              onPage={setPage}
            />
          </>
        )}
      </section>
      <p className="footnote">
        Faculty Finder with availability and smart matching is an upcoming module.
      </p>
    </>
  );
}
