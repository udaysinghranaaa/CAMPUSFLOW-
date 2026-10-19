export function campusDate(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kolkata',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const value = (key: string) => parts.find((p) => p.type === key)!.value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}
export function campusTime(now = new Date()) {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(now);
}
export function addDays(date: string, days: number) {
  const value = new Date(date + 'T00:00:00Z');
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
export function monday(date: string) {
  const day = new Date(date + 'T00:00:00Z').getUTCDay();
  return addDays(date, -((day + 6) % 7));
}
export const minutes = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
export const clockLabel = (time: string) => time.slice(0, 5);
export function classPosition(
  sessions: { date: string; startTime: string; endTime: string }[],
  now = new Date(),
) {
  const date = campusDate(now),
    time = campusTime(now);
  const sorted = [...sessions].sort((a, b) =>
    (a.date + a.startTime).localeCompare(b.date + b.startTime),
  );
  return {
    current: sorted.find(
      (s) => s.date === date && s.startTime.slice(0, 5) <= time && time < s.endTime.slice(0, 5),
    ),
    next: sorted.find((s) => s.date > date || (s.date === date && s.startTime.slice(0, 5) > time)),
  };
}
