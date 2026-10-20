import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import type { Page } from '../../types/api';
import { api, errorMessage } from '../../services/apiClient';
import { useAuth } from '../auth/authContext';
import { RelationshipPicker } from '../management/RelationshipPicker';
import { useScheduleMutation } from './hooks';
import { ScheduleForm } from './ScheduleForm';
import { dateField, timeField, positiveId } from './schemas';
import type { Unavailable } from './types';
import { campusDate, clockLabel } from './time';
import {
  Dialog,
  EmptyState,
  ErrorState,
  LoadingSkeleton,
  Pagination,
} from '../../components/ui/feedback';
const schema = z
  .object({
    facultyId: positiveId,
    date: dateField,
    startTime: timeField,
    endTime: timeField,
    reason: z.string().max(500),
  })
  .refine((v) => v.startTime < v.endTime, {
    path: ['endTime'],
    message: 'End must be after start.',
  });
export function UnavailabilityPanel() {
  const { user } = useAuth();
  const isAdmin = user!.account.role === 'ADMIN';
  const [faculty, setFaculty] = useState(String(user?.faculty?.id ?? ''));
  const [page, setPage] = useState(0);
  const [create, setCreate] = useState(false);
  const [cancel, setCancel] = useState<Unavailable | null>(null);
  const write = useScheduleMutation();
  const query = useQuery({
    queryKey: ['timetable', 'unavailable', faculty, page],
    enabled: !!faculty,
    queryFn: ({ signal }) =>
      api.request<Page<Unavailable>>(
        `/api/v1/timetable/unavailability?facultyId=${faculty}&page=${page}&size=10`,
        { signal },
      ),
  });
  return (
    <section className="panel unavailability-panel">
      <div className="section-heading">
        <h2>{isAdmin ? 'Faculty unavailable blocks' : 'My unavailable blocks'}</h2>
        <button
          className="button secondary small"
          disabled={!faculty}
          onClick={() => setCreate(true)}
        >
          Add unavailable block
        </button>
      </div>
      <p className="muted">
        A conflicting class or draft cannot be silently displaced. Personal reasons are visible only
        to the faculty owner and administrators.
      </p>
      {isAdmin && (
        <div className="field">
          <label htmlFor="block-faculty">Faculty</label>
          <RelationshipPicker
            id="block-faculty"
            resource="faculty"
            label="Faculty"
            value={faculty}
            onChange={(id) => {
              setFaculty(id);
              setPage(0);
            }}
          />
        </div>
      )}
      {!faculty ? (
        <p className="muted">Select a faculty member.</p>
      ) : query.isPending ? (
        <LoadingSkeleton />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : (
        <>
          {query.data.content.length === 0 ? (
            <EmptyState
              title="No unavailable blocks"
              description="This does not prove availability; schedule coverage is evaluated separately."
            />
          ) : (
            query.data.content.map((b) => (
              <article className="block-row" key={b.id}>
                <div>
                  <strong>
                    {b.date} · {clockLabel(b.startTime)}–{clockLabel(b.endTime)}
                  </strong>
                  <p>{b.reason ?? 'No reason provided.'}</p>
                  <small>{b.active ? 'Active block' : 'Cancelled block'}</small>
                </div>
                {b.active && (
                  <button className="text-button" onClick={() => setCancel(b)}>
                    Cancel block
                  </button>
                )}
              </article>
            ))
          )}
          <Pagination
            page={page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onPage={setPage}
          />
        </>
      )}
      {create && (
        <ScheduleForm
          title="Add unavailable block"
          onClose={() => setCreate(false)}
          schema={schema}
          defaults={{
            facultyId: faculty,
            date: campusDate(),
            startTime: '09:00',
            endTime: '10:00',
            reason: '',
          }}
          fields={() => [
            { name: 'date', label: 'Campus date', type: 'date' },
            { name: 'startTime', label: 'Start time', type: 'time' },
            { name: 'endTime', label: 'End time', type: 'time' },
            { name: 'reason', label: 'Private reason', optional: true },
          ]}
          onSubmit={(v) =>
            write.mutateAsync({
              path: '/api/v1/timetable/unavailability',
              method: 'POST',
              body: {
                facultyId: Number(v.facultyId),
                date: v.date,
                startTime: v.startTime,
                endTime: v.endTime,
                reason: v.reason || null,
              },
            })
          }
        />
      )}
      {cancel && (
        <Dialog
          title="Cancel unavailable block?"
          onClose={() => setCancel(null)}
          busy={write.isPending}
        >
          <p>Remove this block from active availability calculations? The record is retained.</p>
          {write.error && (
            <p role="alert" className="form-error">
              {errorMessage(write.error)}
            </p>
          )}
          <footer className="dialog-actions">
            <button
              className="button secondary"
              onClick={() => setCancel(null)}
              disabled={write.isPending}
            >
              Keep block
            </button>
            <button
              className="button primary"
              disabled={write.isPending}
              onClick={() =>
                write.mutate(
                  { path: `/api/v1/timetable/unavailability/${cancel.id}/cancel`, method: 'PATCH' },
                  { onSuccess: () => setCancel(null) },
                )
              }
            >
              Confirm cancellation
            </button>
          </footer>
        </Dialog>
      )}
    </section>
  );
}
