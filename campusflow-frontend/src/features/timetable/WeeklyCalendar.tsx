import { useState } from 'react';
import type { Occurrence } from './types';
import { addDays, clockLabel, minutes } from './time';
import { EmptyState, StatusBadge } from '../../components/ui/feedback';
export function WeeklyCalendar({
  weekStart,
  sessions,
  onSelect,
}: {
  weekStart: string;
  sessions: Occurrence[];
  onSelect?: (session: Occurrence) => void;
}) {
  const [mode, setMode] = useState<'week' | 'day'>('week');
  const [day, setDay] = useState('0');
  const days = Array.from(
    { length: sessions.some((s) => s.date === addDays(weekStart, 6)) ? 7 : 6 },
    (_, i) => addDays(weekStart, i),
  );
  const start = Math.min(
    8 * 60,
    ...sessions.map((s) => Math.floor(minutes(s.startTime) / 60) * 60),
  );
  const end = Math.max(18 * 60, ...sessions.map((s) => Math.ceil(minutes(s.endTime) / 60) * 60));
  const height = (end - start) * 1.1;
  const card = (s: Occurrence) => (
    <>
      <span className="event-time">
        {clockLabel(s.startTime)}–{clockLabel(s.endTime)}
      </span>
      <strong>{s.subjectName}</strong>
      <span>
        {s.facultyName} · {s.roomCode}
      </span>
      <small>
        Section {s.sectionName} · {s.sessionType.toLowerCase()}
      </small>
      {s.status === 'DRAFT' && <StatusBadge value="DRAFT" />}
    </>
  );
  return (
    <section className="panel calendar-panel">
      <div className="calendar-tools">
        <div className="view-toggle">
          <button
            className={`button small ${mode === 'week' ? 'primary' : 'secondary'}`}
            aria-pressed={mode === 'week'}
            onClick={() => setMode('week')}
          >
            Weekly view
          </button>
          <button
            className={`button small ${mode === 'day' ? 'primary' : 'secondary'}`}
            aria-pressed={mode === 'day'}
            onClick={() => setMode('day')}
          >
            Daily view
          </button>
        </div>
        <span className="muted">Asia/Kolkata · Monday start</span>
        {mode === 'day' && (
          <label className="field">
            Day
            <select value={day} onChange={(e) => setDay(e.target.value)}>
              {days.map((date, i) => (
                <option key={date} value={i}>
                  {new Intl.DateTimeFormat('en', { weekday: 'long', timeZone: 'UTC' }).format(
                    new Date(date + 'T00:00:00Z'),
                  )}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      {sessions.length === 0 ? (
        <EmptyState
          title="No classes this week"
          description="No published classes are known for this week. This does not establish faculty availability."
        />
      ) : (
        <>
          <div
            className={`weekly-scroll ${mode === 'day' ? 'hidden-week' : ''}`}
            tabIndex={0}
            role="region"
            aria-label="Weekly timetable grid"
          >
            <div
              className="week-heading"
              style={{ gridTemplateColumns: `50px repeat(${days.length},minmax(115px,1fr))` }}
            >
              <span>IST</span>
              {days.map((date) => (
                <strong key={date}>
                  {new Intl.DateTimeFormat('en', {
                    weekday: 'short',
                    day: 'numeric',
                    month: 'short',
                    timeZone: 'UTC',
                  }).format(new Date(date + 'T00:00:00Z'))}
                </strong>
              ))}
            </div>
            <div
              className="week-columns"
              style={{ gridTemplateColumns: `50px repeat(${days.length},minmax(115px,1fr))` }}
            >
              <div className="time-column" style={{ height }}>
                {Array.from({ length: (end - start) / 60 + 1 }, (_, i) => (
                  <span key={i} style={{ top: i * 66 }}>
                    {String(Math.floor(start / 60) + i).padStart(2, '0')}:00
                  </span>
                ))}
              </div>
              {days.map((date) => (
                <div className="day-column" key={date} style={{ height }}>
                  {sessions
                    .filter((s) => s.date === date)
                    .map((s) => (
                      <div
                        className={`calendar-event ${s.status === 'DRAFT' ? 'draft' : ''}`}
                        key={s.sessionId + '-' + s.date}
                        style={{
                          top: (minutes(s.startTime) - start) * 1.1,
                          height: Math.max(25, (minutes(s.endTime) - minutes(s.startTime)) * 1.1),
                        }}
                      >
                        {onSelect ? (
                          <button
                            onClick={() => onSelect(s)}
                            aria-label={`Edit ${s.subjectName} on ${s.date}`}
                          >
                            {card(s)}
                          </button>
                        ) : (
                          card(s)
                        )}
                      </div>
                    ))}
                </div>
              ))}
            </div>
          </div>
          <div className={`calendar-list ${mode === 'day' ? 'show-list' : ''}`}>
            {(mode === 'day' ? [days[Number(day)]] : days).map((date) => (
              <section key={date}>
                <h3>
                  {new Intl.DateTimeFormat('en', {
                    weekday: 'long',
                    day: 'numeric',
                    month: 'short',
                    timeZone: 'UTC',
                  }).format(new Date(date + 'T00:00:00Z'))}
                </h3>
                {sessions.filter((s) => s.date === date).length === 0 ? (
                  <p className="muted">No known classes.</p>
                ) : (
                  sessions
                    .filter((s) => s.date === date)
                    .map((s) => (
                      <article className="calendar-list-event" key={s.sessionId + '-' + date}>
                        {onSelect ? (
                          <button className="text-button" onClick={() => onSelect(s)}>
                            {card(s)}
                          </button>
                        ) : (
                          card(s)
                        )}
                      </article>
                    ))
                )}
              </section>
            ))}
          </div>
        </>
      )}
    </section>
  );
}
