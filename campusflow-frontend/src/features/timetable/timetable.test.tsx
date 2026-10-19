import { beforeEach, describe, it, expect, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWorkspace } from '../../test/render';
import { json, me, page, tokens, failure, department } from '../../test/fixtures';
import { api } from '../../services/apiClient';
import { WeeklyCalendar } from './WeeklyCalendar';
import { FinderPage, AvailabilityBadge } from './FinderPage';
import { BuilderPage } from './BuilderPage';
import { OwnTimetablePage } from './OwnTimetablePage';
import { campusDate, campusTime, monday, addDays, classPosition } from './time';
import type { Occurrence, Session, Term, Availability } from './types';
const term: Term = {
  id: 1,
  name: 'Autumn 2026',
  departmentId: 1,
  semester: 1,
  startDate: '2026-10-19',
  endDate: '2026-11-02',
  active: true,
};
const session: Session = {
  id: 1,
  academicTermId: 1,
  sectionId: 1,
  sectionName: 'A',
  subjectId: 1,
  subjectName: 'Algorithms',
  facultyId: 1,
  facultyName: 'Test Faculty',
  roomId: 1,
  roomCode: 'R1',
  dayOfWeek: 1,
  startTime: '09:00:00',
  endTime: '10:00:00',
  effectiveStartDate: '2026-10-19',
  effectiveEndDate: '2026-11-02',
  status: 'DRAFT',
  sessionType: 'LECTURE',
};
const occurrence: Occurrence = {
  sessionId: 1,
  date: '2026-10-19',
  startTime: '09:00:00',
  endTime: '10:00:00',
  subjectName: 'Algorithms',
  facultyName: 'Test Faculty',
  roomCode: 'R1',
  sectionId: 1,
  sectionName: 'A',
  sessionType: 'LECTURE',
  status: 'PUBLISHED',
};
let denied = false;
let role: 'ADMIN' | 'STUDENT' | 'FACULTY' = 'ADMIN';
beforeEach(async () => {
  api.endSession(null);
  role = 'ADMIN';
  denied = false;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input));
      if (url.pathname.endsWith('/login')) return json(tokens());
      if (url.pathname.endsWith('/auth/me')) return json(me(role));
      if (url.pathname === '/api/v1/timetable/terms') return json(page([term]));
      if (url.pathname === '/api/v1/timetable/sessions' && init?.method === 'POST')
        return denied
          ? failure(409, 'Faculty scheduling conflict. Choose a different slot.')
          : json(session, 201);
      if (url.pathname === '/api/v1/timetable/sessions') return json(page([session]));
      if (url.pathname === '/api/v1/timetable/sessions/1/publish')
        return denied
          ? failure(409, 'Room scheduling conflict.')
          : json({ ...session, status: 'PUBLISHED' });
      if (
        url.pathname.includes('/week') ||
        url.pathname.endsWith('/student/me') ||
        url.pathname.endsWith('/faculty/me')
      )
        return json({ weekStart: '2026-10-19', timezone: 'Asia/Kolkata', sessions: [occurrence] });
      if (url.pathname === '/api/v1/faculty-finder')
        return json(
          page([
            {
              id: 1,
              fullName: 'Test Faculty',
              departmentCode: 'CSE',
              designation: 'Lecturer',
              subjects: ['Algorithms'],
              state: 'UNKNOWN',
              explanation: 'Coverage is incomplete.',
              date: '2026-10-19',
              startTime: '09:00',
              endTime: '10:00',
            },
          ]),
        );
      if (url.pathname === '/api/v1/departments') return json(page([department]));
      if (url.pathname === '/api/v1/faculty')
        return json(page([{ id: 1, fullName: 'Test Faculty' }]));
      if (url.pathname === '/api/v1/subjects') return json(page([{ id: 1, name: 'Algorithms' }]));
      if (url.pathname === '/api/v1/rooms')
        return json(page([{ id: 1, name: 'Room', code: 'R1' }]));
      if (url.pathname === '/api/v1/sections') return json(page([{ id: 1, name: 'A' }]));
      return json(page([]));
    }),
  );
  await api.login('test@example.edu', 'fixture-password');
});
describe('campus-local timetable and finder', () => {
  it('formats the campus date across UTC midnight and starts Monday', () => {
    expect(campusDate(new Date('2026-10-18T20:00:00Z'))).toBe('2026-10-19');
    expect(campusTime(new Date('2026-10-18T20:00:00Z'))).toBe('01:30');
    expect(monday('2026-10-25')).toBe('2026-10-19');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });
  it('treats class end as exclusive for current/next class', () => {
    const value = classPosition([occurrence], new Date('2026-10-19T04:30:00Z'));
    expect(value.current).toBeUndefined();
    expect(value.next).toBeUndefined();
  });
  it('renders real subject/faculty/room and switches to daily view', async () => {
    renderWorkspace(<WeeklyCalendar weekStart="2026-10-19" sessions={[occurrence]} />);
    expect(
      within(screen.getByRole('region', { name: 'Weekly timetable grid' })).getByText('Algorithms'),
    ).toBeInTheDocument();
    expect(screen.getAllByText('Test Faculty · R1').length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Daily view' }));
    expect(screen.getByRole('combobox', { name: 'Day' })).toBeInTheDocument();
  });
  it('explains that no class is not proof of availability', () => {
    renderWorkspace(<WeeklyCalendar weekStart="2026-10-19" sessions={[]} />);
    expect(screen.getByText('No classes this week')).toBeInTheDocument();
    expect(screen.getByText(/does not establish faculty availability/)).toBeInTheDocument();
  });
  it.each([
    ['FREE', 'Scheduled free'],
    ['BUSY', 'Teaching busy'],
    ['UNKNOWN', 'Unknown'],
    ['UNAVAILABLE', 'Unavailable'],
  ] as const)('distinguishes %s availability', (state, label) => {
    renderWorkspace(<AvailabilityBadge state={state as Availability} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
  it('sends faculty finder filters through the real typed HTTP client', async () => {
    renderWorkspace(<FinderPage />);
    await screen.findByRole('heading', { name: 'Test Faculty' });
    await userEvent.type(screen.getByLabelText('Faculty name'), 'Literal_%!');
    await userEvent.selectOptions(screen.getByLabelText('Availability'), 'FREE');
    await userEvent.click(screen.getByRole('button', { name: 'Find faculty' }));
    await waitFor(() =>
      expect(
        vi.mocked(fetch).mock.calls.some(([url]) => {
          const value = new URL(String(url));
          return (
            value.searchParams.get('name') === 'Literal_%!' &&
            value.searchParams.get('state') === 'FREE'
          );
        }),
      ).toBe(true),
    );
    expect(screen.getByText(/does not guarantee physical availability/)).toBeInTheDocument();
  });
  it('validates ordered finder time windows before submitting', async () => {
    renderWorkspace(<FinderPage />);
    await screen.findByRole('heading', { name: 'Test Faculty' });
    await userEvent.clear(screen.getByLabelText('End time'));
    await userEvent.type(screen.getByLabelText('End time'), '08:00');
    await userEvent.click(screen.getByRole('button', { name: 'Find faculty' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('ordered start/end time');
  });
  it('shows a409 publish conflict and preserves the management dialog', async () => {
    denied = true;
    renderWorkspace(<BuilderPage />);
    await waitFor(() =>
      expect(screen.getByRole('option', { name: 'Autumn 2026 · semester 1' })).toBeInTheDocument(),
    );
    await userEvent.selectOptions(screen.getByLabelText('Academic term'), '1');
    await userEvent.click(await screen.findByRole('button', { name: 'Manage Algorithms' }));
    await userEvent.click(screen.getByRole('button', { name: 'Publish' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm publish' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Room scheduling conflict.');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
  it('shows student timetable without mutation controls or another profile ID', async () => {
    role = 'STUDENT';
    await api.login('test@example.edu', 'fixture-password');
    renderWorkspace(<OwnTimetablePage />);
    expect(await screen.findByRole('heading', { name: 'My timetable' })).toBeInTheDocument();
    await waitFor(() =>
      expect(
        vi
          .mocked(fetch)
          .mock.calls.some(
            ([url]) => new URL(String(url)).pathname === '/api/v1/timetable/student/me',
          ),
      ).toBe(true),
    );
    expect(screen.queryByRole('button', { name: 'Create session' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add unavailable block' })).not.toBeInTheDocument();
  });
});
