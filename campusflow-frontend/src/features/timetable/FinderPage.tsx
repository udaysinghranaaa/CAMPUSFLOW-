import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import { api } from '../../services/apiClient';
import { queryString } from '../../services/resources';
import type { Page } from '../../types/api';
import type { FoundFaculty, Availability } from './types';
import { campusDate } from './time';
import { RelationshipPicker } from '../management/RelationshipPicker';
import {
  Dialog,
  EmptyState,
  ErrorState,
  LoadingSkeleton,
  PageHeader,
  Pagination,
} from '../../components/ui/feedback';
export function AvailabilityBadge({ state }: { state: Availability }) {
  return (
    <span className={`availability ${state.toLowerCase()}`}>
      {state === 'FREE'
        ? 'Scheduled free'
        : state === 'BUSY'
          ? 'Teaching busy'
          : state === 'UNAVAILABLE'
            ? 'Unavailable'
            : 'Unknown'}
    </span>
  );
}
export function FinderPage() {
  const [search] = useSearchParams();
  const [selected, setSelected] = useState<number | null>(Number(search.get('facultyId')) || null);
  const initial = {
    name: '',
    departmentId: '',
    subjectId: '',
    state: '',
    date: campusDate(),
    startTime: '09:00',
    endTime: '10:00',
  };
  const [draft, setDraft] = useState(initial);
  const [filters, setFilters] = useState(initial);
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const query = useQuery({
    queryKey: ['faculty-finder', filters, page],
    queryFn: ({ signal }) =>
      api.request<Page<FoundFaculty>>(
        '/api/v1/faculty-finder' + queryString({ ...filters, page, size: 10 }),
        { signal },
      ),
  });
  const detail = useQuery({
    queryKey: [
      'faculty-finder',
      'detail',
      selected,
      filters.date,
      filters.startTime,
      filters.endTime,
    ],
    queryFn: ({ signal }) =>
      api.request<FoundFaculty>(
        `/api/v1/faculty-finder/${selected}/availability` +
          queryString({
            date: filters.date,
            startTime: filters.startTime,
            endTime: filters.endTime,
          }),
        { signal },
      ),
    enabled: selected !== null,
  });
  return (
    <>
      <PageHeader
        eyebrow="SCHEDULE-AWARE CONNECTIONS"
        title="Faculty Finder"
        description="Find your faculty and check known teaching availability."
      />
      <p className="info-banner">
        Scheduled free means no known teaching or unavailable-block conflict within confirmed
        schedule coverage. It does not guarantee physical availability on campus.
      </p>
      <section className="panel">
        <form
          aria-label="Faculty Finder filters"
          onSubmit={(e) => {
            e.preventDefault();
            if (!draft.date || !draft.startTime || draft.startTime >= draft.endTime) {
              setError('Select a date and an ordered start/end time.');
              return;
            }
            setError('');
            setFilters(draft);
            setPage(0);
          }}
        >
          <div className="filter-fields">
            <div className="field">
              <label htmlFor="finder-name">Faculty name</label>
              <input
                id="finder-name"
                type="search"
                maxLength={150}
                value={draft.name}
                onChange={(e) => setDraft({ ...draft, name: e.target.value })}
              />
            </div>
            <div className="field">
              <label htmlFor="finder-department">Department</label>
              <RelationshipPicker
                resource="departments"
                id="finder-department"
                label="Department"
                optional
                value={draft.departmentId}
                onChange={(value) => setDraft({ ...draft, departmentId: value, subjectId: '' })}
              />
            </div>
            <div className="field">
              <label htmlFor="finder-subject">Subject</label>
              <RelationshipPicker
                key={draft.departmentId}
                resource="subjects"
                id="finder-subject"
                label="Subject"
                optional
                value={draft.subjectId}
                params={draft.departmentId ? { departmentId: draft.departmentId } : {}}
                onChange={(value) => setDraft({ ...draft, subjectId: value })}
              />
            </div>
            {(['date', 'startTime', 'endTime'] as const).map((name) => (
              <div className="field" key={name}>
                <label htmlFor={`finder-${name}`}>
                  {name === 'date'
                    ? 'Campus date'
                    : name === 'startTime'
                      ? 'Start time'
                      : 'End time'}
                </label>
                <input
                  id={`finder-${name}`}
                  type={name === 'date' ? 'date' : 'time'}
                  value={draft[name]}
                  onChange={(e) => setDraft({ ...draft, [name]: e.target.value })}
                />
              </div>
            ))}
            <div className="field">
              <label htmlFor="finder-state">Availability</label>
              <select
                id="finder-state"
                value={draft.state}
                onChange={(e) => setDraft({ ...draft, state: e.target.value })}
              >
                <option value="">All states</option>
                {['FREE', 'BUSY', 'UNKNOWN', 'UNAVAILABLE'].map((state) => (
                  <option key={state}>{state}</option>
                ))}
              </select>
            </div>
          </div>
          {error && (
            <p role="alert" className="form-error">
              {error}
            </p>
          )}
          <button className="button primary">Find faculty</button>
        </form>
      </section>
      <section className="panel finder-results">
        {query.isPending ? (
          <LoadingSkeleton label="Evaluating faculty schedules…" />
        ) : query.isError ? (
          <ErrorState error={query.error} retry={() => void query.refetch()} />
        ) : (
          <>
            {query.data.content.length === 0 ? (
              <EmptyState
                title="No faculty matched"
                description="Try another department, subject or time window."
              />
            ) : (
              <div className="finder-grid">
                {query.data.content.map((f) => (
                  <article className="finder-card" key={f.id}>
                    <div>
                      <span className="eyebrow">{f.departmentCode}</span>
                      <h2>{f.fullName}</h2>
                      <p className="muted">{f.designation ?? 'Faculty member'}</p>
                    </div>
                    <AvailabilityBadge state={f.state} />
                    <p>{f.explanation}</p>
                    <p className="muted">
                      Known subjects: {f.subjects.join(', ') || 'Not yet scheduled'}
                    </p>
                    <button className="button secondary small" onClick={() => setSelected(f.id)}>
                      View {f.fullName}
                    </button>
                  </article>
                ))}
              </div>
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
      {selected !== null && (
        <Dialog title="Faculty availability" onClose={() => setSelected(null)}>
          {detail.isPending ? (
            <LoadingSkeleton />
          ) : detail.isError ? (
            <ErrorState error={detail.error} retry={() => void detail.refetch()} />
          ) : (
            <>
              <h2>{detail.data.fullName}</h2>
              <p>
                {detail.data.departmentCode} · {detail.data.designation}
              </p>
              <AvailabilityBadge state={detail.data.state} />
              <p className="info-banner">{detail.data.explanation}</p>
              <p>
                {detail.data.date} · {detail.data.startTime.slice(0, 5)}–
                {detail.data.endTime.slice(0, 5)} Asia/Kolkata
              </p>
              <p className="muted">
                Known subjects:{' '}
                {detail.data.subjects.join(', ') || 'No confirmed teaching information.'}
              </p>
            </>
          )}
        </Dialog>
      )}
    </>
  );
}
