import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api, errorMessage } from '../../services/apiClient';
import type { Page } from '../../types/api';
import { DataTable } from '../../components/ui/DataTable';
import {
  Dialog,
  EmptyState,
  ErrorState,
  LoadingSkeleton,
  PageHeader,
  Pagination,
  StatusBadge,
} from '../../components/ui/feedback';
import type { Term, Session, Weekly } from './types';
import { WeeklyCalendar } from './WeeklyCalendar';
import { SessionEditor } from './SessionEditor';
import { ExceptionEditor } from './ExceptionEditor';
import { UnavailabilityPanel } from './UnavailabilityPanel';
import { campusDate, monday, addDays, clockLabel } from './time';
import { useScheduleMutation } from './hooks';
export function BuilderPage() {
  const [termPage, setTermPage] = useState(0);
  const [term, setTerm] = useState<Term | null>(null);
  const [page, setPage] = useState(0);
  const [week, setWeek] = useState(monday(campusDate()));
  const [editor, setEditor] = useState<{ session?: Session } | null>(null);
  const [active, setActive] = useState<Session | null>(null);
  const [exception, setException] = useState<Session | null>(null);
  const [action, setAction] = useState<'publish' | 'cancel' | null>(null);
  const [error, setError] = useState('');
  const write = useScheduleMutation();
  const terms = useQuery({
    queryKey: ['timetable', 'term-choices', termPage],
    queryFn: ({ signal }) =>
      api.request<Page<Term>>(`/api/v1/timetable/terms?active=true&page=${termPage}&size=20`, {
        signal,
      }),
  });
  const series = useQuery({
    queryKey: ['timetable', 'series', term?.id, page],
    enabled: !!term,
    queryFn: ({ signal }) =>
      api.request<Page<Session>>(
        `/api/v1/timetable/sessions?academicTermId=${term!.id}&page=${page}&size=10`,
        { signal },
      ),
  });
  const weekly = useQuery({
    queryKey: ['timetable', 'admin-week', term?.id, week],
    enabled: !!term,
    queryFn: ({ signal }) =>
      api.request<Weekly>(
        `/api/v1/timetable/admin/week?academicTermId=${term!.id}&weekStart=${week}`,
        { signal },
      ),
  });
  const open = (session: Session) => {
    write.reset();
    setError('');
    setAction(null);
    setActive(session);
  };
  return (
    <>
      <PageHeader
        eyebrow="ACADEMIC OPERATIONS"
        title="Smart timetable builder"
        description="Create weekly teaching sessions. Conflicts are enforced by PostgreSQL, including drafts."
        action={
          <button className="button primary" disabled={!term} onClick={() => setEditor({})}>
            Create session
          </button>
        }
      />
      <section className="panel week-controls">
        <div className="field">
          <label htmlFor="term-selector">Academic term</label>
          {terms.isError ? (
            <ErrorState error={terms.error} retry={() => void terms.refetch()} />
          ) : (
            <select
              id="term-selector"
              value={term?.id ?? ''}
              onChange={(e) => {
                setTerm(terms.data?.content.find((t) => String(t.id) === e.target.value) ?? null);
                setPage(0);
              }}
            >
              <option value="">Select a term</option>
              {term && !terms.data?.content.some((t) => t.id === term.id) && (
                <option value={term.id}>{term.name}</option>
              )}
              {terms.data?.content.map((t) => (
                <option value={t.id} key={t.id}>
                  {t.name} · semester {t.semester}
                </option>
              ))}
            </select>
          )}
          <div className="choice-pagination">
            <span>{terms.data?.totalElements ?? 0} active terms</span>
            <button
              aria-label="Previous term choices"
              disabled={termPage === 0}
              onClick={() => setTermPage(termPage - 1)}
            >
              ‹
            </button>
            <button
              aria-label="Next term choices"
              disabled={!terms.data || terms.data.last}
              onClick={() => setTermPage(termPage + 1)}
            >
              ›
            </button>
          </div>
        </div>
        <Link className="button secondary small" to="/admin/terms">
          Manage terms &amp; coverage
        </Link>
        <button className="button secondary small" onClick={() => setWeek(addDays(week, -7))}>
          Previous week
        </button>
        <label className="field">
          Week containing
          <input
            type="date"
            aria-label="Week containing"
            value={week}
            onChange={(e) => {
              if (e.target.value) setWeek(monday(e.target.value));
            }}
          />
        </label>
        <button className="button secondary small" onClick={() => setWeek(addDays(week, 7))}>
          Next week
        </button>
      </section>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {!term ? (
        <EmptyState
          title="Choose an academic term"
          description="Create a term first, then select it to plan teaching sessions."
        />
      ) : (
        <>
          {weekly.isPending ? (
            <LoadingSkeleton />
          ) : weekly.isError ? (
            <ErrorState error={weekly.error} retry={() => void weekly.refetch()} />
          ) : (
            <WeeklyCalendar
              weekStart={week}
              sessions={weekly.data.sessions}
              onSelect={(item) => {
                void api
                  .request<Session>(`/api/v1/timetable/sessions/${item.sessionId}`)
                  .then(open)
                  .catch((problem) => setError(errorMessage(problem)));
              }}
            />
          )}
          <section className="panel data-panel series-panel">
            {series.isPending ? (
              <LoadingSkeleton />
            ) : series.isError ? (
              <ErrorState error={series.error} retry={() => void series.refetch()} />
            ) : (
              <>
                {series.data.content.length === 0 ? (
                  <EmptyState title="No recurring sessions" />
                ) : (
                  <DataTable
                    caption="Recurring timetable sessions"
                    rows={series.data.content}
                    columns={[
                      {
                        key: 'subject',
                        label: 'Subject',
                        render: (s) => <strong>{s.subjectName}</strong>,
                      },
                      {
                        key: 'who',
                        label: 'Faculty / section',
                        render: (s) => `${s.facultyName} · ${s.sectionName}`,
                      },
                      {
                        key: 'when',
                        label: 'Campus day / time',
                        render: (s) =>
                          `${['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][s.dayOfWeek - 1]} ${clockLabel(s.startTime)}–${clockLabel(s.endTime)}`,
                      },
                      { key: 'room', label: 'Room', render: (s) => s.roomCode },
                      {
                        key: 'status',
                        label: 'State',
                        render: (s) => <StatusBadge value={s.status} />,
                      },
                      {
                        key: 'actions',
                        label: 'Actions',
                        render: (s) => (
                          <button className="text-button" onClick={() => open(s)}>
                            Manage {s.subjectName}
                          </button>
                        ),
                      },
                    ]}
                  />
                )}
                <Pagination
                  page={page}
                  totalPages={series.data.totalPages}
                  totalElements={series.data.totalElements}
                  onPage={setPage}
                />
              </>
            )}
          </section>
        </>
      )}
      <UnavailabilityPanel />
      {term && editor && (
        <SessionEditor term={term} session={editor.session} onClose={() => setEditor(null)} />
      )}{' '}
      {term && exception && (
        <ExceptionEditor term={term} session={exception} onClose={() => setException(null)} />
      )}{' '}
      {active && (
        <Dialog
          title={`Manage ${active.subjectName}`}
          busy={write.isPending}
          onClose={() => setActive(null)}
        >
          <p>
            {active.facultyName} · Section {active.sectionName} · {active.roomCode}
          </p>
          <p>
            {active.effectiveStartDate}–{active.effectiveEndDate} · {active.status}
          </p>
          {action ? (
            <>
              <p className="info-banner">
                {action === 'cancel'
                  ? 'Cancel the entire recurring series? Active allocations for all its dates will be released, while the series record is retained.'
                  : 'Publish this series? Only published sessions are visible to students and faculty.'}
              </p>
              {write.error && (
                <p className="form-error" role="alert">
                  {errorMessage(write.error)}
                </p>
              )}
              <footer className="dialog-actions">
                <button
                  className="button secondary"
                  disabled={write.isPending}
                  onClick={() => setAction(null)}
                >
                  Back
                </button>
                <button
                  className={`button ${action === 'cancel' ? 'danger' : 'primary'}`}
                  disabled={write.isPending}
                  onClick={() =>
                    write.mutate(
                      {
                        path: `/api/v1/timetable/sessions/${active.id}/${action}`,
                        method: 'PATCH',
                      },
                      {
                        onSuccess: () => {
                          setActive(null);
                          setAction(null);
                        },
                      },
                    )
                  }
                >
                  {write.isPending
                    ? 'Updating…'
                    : action === 'cancel'
                      ? 'Confirm cancellation'
                      : 'Confirm publish'}
                </button>
              </footer>
            </>
          ) : (
            <footer className="dialog-actions">
              <button
                className="button secondary"
                disabled={active.status === 'CANCELLED'}
                onClick={() => {
                  setEditor({ session: active });
                  setActive(null);
                }}
              >
                Edit series
              </button>
              <button
                className="button secondary"
                disabled={active.status === 'CANCELLED'}
                onClick={() => {
                  setException(active);
                  setActive(null);
                }}
              >
                Change one date
              </button>
              {active.status === 'DRAFT' && (
                <button className="button primary" onClick={() => setAction('publish')}>
                  Publish
                </button>
              )}
              <button
                className="button danger"
                disabled={active.status === 'CANCELLED'}
                onClick={() => setAction('cancel')}
              >
                Cancel series
              </button>
            </footer>
          )}
        </Dialog>
      )}
    </>
  );
}
