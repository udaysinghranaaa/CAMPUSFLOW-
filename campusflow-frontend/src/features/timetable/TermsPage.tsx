import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { api } from '../../services/apiClient';
import type { Page } from '../../types/api';
import { DataTable } from '../../components/ui/DataTable';
import {
  PageHeader,
  Pagination,
  LoadingSkeleton,
  ErrorState,
  EmptyState,
  StatusBadge,
} from '../../components/ui/feedback';
import { ScheduleForm } from './ScheduleForm';
import { requiredText, positiveId, dateField, timeField } from './schemas';
import { useScheduleMutation } from './hooks';
import type { Term } from './types';
import { campusDate, addDays } from './time';
const termSchema = z
  .object({
    name: requiredText.max(150),
    departmentId: positiveId,
    semester: positiveId,
    startDate: dateField,
    endDate: dateField,
    active: z.enum(['true', 'false']),
  })
  .refine(
    (v) =>
      v.startDate <= v.endDate &&
      new Date(v.endDate).getTime() - new Date(v.startDate).getTime() <= 366 * 86400000,
    { path: ['endDate'], message: 'Term must be ordered and at most 366 days.' },
  );
const coverageSchema = z
  .object({
    facultyId: positiveId,
    dayMask: z
      .string()
      .refine((v) => Number(v) >= 1 && Number(v) <= 127, 'Select at least one weekday.'),
    startTime: timeField,
    endTime: timeField,
  })
  .refine((v) => v.startTime < v.endTime, {
    path: ['endTime'],
    message: 'End must be later than start.',
  });
export function TermsPage() {
  const [page, setPage] = useState(0);
  const [create, setCreate] = useState(false);
  const [coverage, setCoverage] = useState<Term | null>(null);
  const write = useScheduleMutation();
  const query = useQuery({
    queryKey: ['timetable', 'terms', page],
    queryFn: ({ signal }) =>
      api.request<Page<Term>>(`/api/v1/timetable/terms?page=${page}&size=10`, { signal }),
  });
  return (
    <>
      <PageHeader
        eyebrow="ACADEMIC PLANNING"
        title="Academic terms"
        description="Department-scoped periods and semester alignment. Campus time: Asia/Kolkata."
        action={
          <button className="button primary" onClick={() => setCreate(true)}>
            Create term
          </button>
        }
      />
      <p className="info-banner">
        Confirm coverage only when this faculty member’s teaching schedule is complete for the
        stated days and time window. Missing coverage remains UNKNOWN.
      </p>
      <section className="panel data-panel">
        {query.isPending ? (
          <LoadingSkeleton />
        ) : query.isError ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : (
          <>
            {query.data.content.length === 0 ? (
              <EmptyState title="No terms yet" />
            ) : (
              <DataTable
                caption="Academic terms"
                rows={query.data.content}
                columns={[
                  { key: 'name', label: 'Term', render: (t) => <strong>{t.name}</strong> },
                  {
                    key: 'dates',
                    label: 'Campus dates',
                    render: (t) => `${t.startDate} – ${t.endDate}`,
                  },
                  { key: 'semester', label: 'Semester', render: (t) => t.semester },
                  {
                    key: 'active',
                    label: 'Status',
                    render: (t) => <StatusBadge value={t.active} />,
                  },
                  {
                    key: 'coverage',
                    label: 'Coverage',
                    render: (t) => (
                      <button className="text-button" onClick={() => setCoverage(t)}>
                        Confirm faculty coverage
                      </button>
                    ),
                  },
                ]}
              />
            )}
            <Pagination
              page={page}
              totalElements={query.data.totalElements}
              totalPages={query.data.totalPages}
              onPage={setPage}
            />
          </>
        )}
      </section>
      {create && (
        <ScheduleForm
          title="Create academic term"
          onClose={() => setCreate(false)}
          defaults={{
            name: '',
            departmentId: '',
            semester: '1',
            startDate: campusDate(),
            endDate: addDays(campusDate(), 90),
            active: 'true',
          }}
          schema={termSchema}
          fields={() => [
            { name: 'name', label: 'Term name' },
            {
              name: 'departmentId',
              label: 'Department',
              type: 'relation',
              resource: 'departments',
            },
            { name: 'semester', label: 'Semester', type: 'number' },
            { name: 'startDate', label: 'Start date', type: 'date' },
            { name: 'endDate', label: 'End date', type: 'date' },
            { name: 'active', label: 'Active', type: 'select', options: ['true', 'false'] },
          ]}
          onSubmit={(v) =>
            write.mutateAsync({
              path: '/api/v1/timetable/terms',
              method: 'POST',
              body: {
                name: v.name,
                departmentId: Number(v.departmentId),
                semester: Number(v.semester),
                startDate: v.startDate,
                endDate: v.endDate,
                active: v.active === 'true',
              },
            })
          }
        />
      )}
      {coverage && (
        <ScheduleForm
          title={`Confirm coverage · ${coverage.name}`}
          onClose={() => setCoverage(null)}
          note="This certifies schedule knowledge, not physical presence. Select the weekdays and time window whose teaching schedule is complete."
          defaults={{ facultyId: '', dayMask: '63', startTime: '08:00', endTime: '18:00' }}
          schema={coverageSchema}
          fields={() => [
            {
              name: 'facultyId',
              label: 'Faculty',
              type: 'relation',
              resource: 'faculty',
              params: { departmentId: String(coverage.departmentId), active: 'true' },
            },
            { name: 'dayMask', label: 'Covered weekdays', type: 'days' },
            { name: 'startTime', label: 'Coverage start', type: 'time' },
            { name: 'endTime', label: 'Coverage end', type: 'time' },
          ]}
          onSubmit={(v) =>
            write.mutateAsync({
              path: `/api/v1/timetable/terms/${coverage.id}/coverage`,
              method: 'PUT',
              body: {
                facultyId: Number(v.facultyId),
                dayMask: Number(v.dayMask),
                startTime: v.startTime,
                endTime: v.endTime,
              },
            })
          }
        />
      )}
    </>
  );
}
