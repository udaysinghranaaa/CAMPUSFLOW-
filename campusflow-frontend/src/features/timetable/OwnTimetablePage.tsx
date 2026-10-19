import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../../services/apiClient';
import { useAuth } from '../auth/authContext';
import { PageHeader, LoadingSkeleton, ErrorState } from '../../components/ui/feedback';
import type { Weekly } from './types';
import { WeeklyCalendar } from './WeeklyCalendar';
import { campusDate, monday, addDays, classPosition, clockLabel } from './time';
import { UnavailabilityPanel } from './UnavailabilityPanel';
export function OwnTimetablePage() {
  const { user } = useAuth();
  const faculty = user!.account.role === 'FACULTY';
  const [week, setWeek] = useState(monday(campusDate()));
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30000);
    return () => clearInterval(timer);
  }, []);
  const query = useQuery({
    queryKey: ['timetable', 'own', user!.account.id, week],
    queryFn: ({ signal }) =>
      api.request<Weekly>(
        `/api/v1/timetable/${faculty ? 'faculty' : 'student'}/me?weekStart=${week}`,
        { signal },
      ),
  });
  const position = classPosition(query.data?.sessions ?? [], now);
  return (
    <>
      <PageHeader
        eyebrow="YOUR ACADEMIC WEEK"
        title={faculty ? 'My teaching timetable' : 'My timetable'}
        description="Published sessions for your own account. All times are Asia/Kolkata."
        action={
          <Link
            className="button secondary"
            to={
              faculty ? `/app/faculty-finder?facultyId=${user?.faculty?.id}` : '/app/faculty-finder'
            }
          >
            Faculty Finder
          </Link>
        }
      />
      <div className="panel week-controls">
        <button className="button secondary small" onClick={() => setWeek(addDays(week, -7))}>
          Previous week
        </button>
        <label className="field">
          Week containing
          <input
            aria-label="Week containing"
            type="date"
            value={week}
            onChange={(e) => {
              if (e.target.value) setWeek(monday(e.target.value));
            }}
          />
        </label>
        <button className="button secondary small" onClick={() => setWeek(addDays(week, 7))}>
          Next week
        </button>
      </div>
      <div className="class-summary">
        <p>
          <strong>Current class:</strong>{' '}
          {position.current
            ? `${'subjectName' in position.current ? position.current.subjectName : ''} until ${clockLabel(position.current.endTime)}`
            : 'No known class in the displayed week at the current campus time.'}
        </p>
        <p>
          <strong>Next known class:</strong>{' '}
          {position.next
            ? `${position.next.date} at ${clockLabel(position.next.startTime)}`
            : 'No upcoming class in the displayed week.'}
        </p>
      </div>
      {query.isPending ? (
        <LoadingSkeleton label="Loading your timetable…" />
      ) : query.isError ? (
        <ErrorState error={query.error} retry={() => void query.refetch()} />
      ) : (
        <WeeklyCalendar weekStart={week} sessions={query.data.sessions} />
      )}{' '}
      {faculty && <UnavailabilityPanel />}
    </>
  );
}
