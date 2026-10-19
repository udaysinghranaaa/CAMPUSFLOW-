import type { ScheduleField } from './ScheduleForm';
import { z } from 'zod';
import { ScheduleForm, positiveId, dateField, timeField } from './ScheduleForm';
import type { Term, Session } from './types';
import { useScheduleMutation } from './hooks';
const schema = z
  .object({
    sectionId: positiveId,
    subjectId: positiveId,
    facultyId: positiveId,
    roomId: positiveId,
    batchId: z.string(),
    dayOfWeek: positiveId.refine((v) => Number(v) <= 7, 'Choose a day1–7.'),
    startTime: timeField,
    endTime: timeField,
    effectiveStartDate: dateField,
    effectiveEndDate: dateField,
    status: z.enum(['DRAFT', 'PUBLISHED']),
    sessionType: z.enum(['LECTURE', 'PRACTICAL', 'TUTORIAL']),
  })
  .refine((v) => v.startTime < v.endTime, { path: ['endTime'], message: 'End must follow start.' })
  .refine((v) => v.effectiveStartDate <= v.effectiveEndDate, {
    path: ['effectiveEndDate'],
    message: 'End date must follow start date.',
  });
export function SessionEditor({
  term,
  session,
  onClose,
}: {
  term: Term;
  session?: Session;
  onClose: () => void;
}) {
  const write = useScheduleMutation();
  return (
    <ScheduleForm
      title={session ? 'Edit recurring session' : 'Create recurring session'}
      note={`Term: ${term.name}. Both drafts and published sessions reserve resources. Start/end intervals are half-open; adjacent sessions are allowed.`}
      onClose={onClose}
      schema={schema}
      defaults={{
        batchId: '',
        sectionId: session ? String(session.sectionId) : '',
        subjectId: session ? String(session.subjectId) : '',
        facultyId: session ? String(session.facultyId) : '',
        roomId: session ? String(session.roomId) : '',
        dayOfWeek: String(session?.dayOfWeek ?? 1),
        startTime: session?.startTime.slice(0, 5) ?? '09:00',
        endTime: session?.endTime.slice(0, 5) ?? '10:00',
        effectiveStartDate: session?.effectiveStartDate ?? term.startDate,
        effectiveEndDate: session?.effectiveEndDate ?? term.endDate,
        status: session?.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT',
        sessionType: session?.sessionType ?? 'LECTURE',
      }}
      fields={(v): ScheduleField[] => [
        {
          name: 'batchId',
          label: 'Filter academic batch',
          type: 'relation',
          resource: 'batches',
          params: { departmentId: String(term.departmentId) },
          optional: true,
        },
        {
          name: 'sectionId',
          label: 'Section',
          type: 'relation',
          resource: 'sections',
          params: v.batchId ? { batchId: v.batchId } : undefined,
        },
        {
          name: 'subjectId',
          label: 'Subject',
          type: 'relation',
          resource: 'subjects',
          params: { departmentId: String(term.departmentId), active: 'true' },
        },
        {
          name: 'facultyId',
          label: 'Faculty',
          type: 'relation',
          resource: 'faculty',
          params: { departmentId: String(term.departmentId), active: 'true' },
        },
        {
          name: 'roomId',
          label: 'Room',
          type: 'relation',
          resource: 'rooms',
          params: { active: 'true' },
        },
        {
          name: 'dayOfWeek',
          label: 'Weekday (Monday=1)',
          type: 'select',
          options: ['1', '2', '3', '4', '5', '6', '7'],
        },
        { name: 'startTime', label: 'Start time', type: 'time' },
        { name: 'endTime', label: 'End time', type: 'time' },
        { name: 'effectiveStartDate', label: 'Effective start date', type: 'date' },
        { name: 'effectiveEndDate', label: 'Effective end date', type: 'date' },
        {
          name: 'sessionType',
          label: 'Session type',
          type: 'select',
          options: ['LECTURE', 'PRACTICAL', 'TUTORIAL'],
        },
        {
          name: 'status',
          label: 'Scheduling state',
          type: 'select',
          options: ['DRAFT', 'PUBLISHED'],
        },
      ]}
      onSubmit={(v) =>
        write.mutateAsync({
          path: session ? `/api/v1/timetable/sessions/${session.id}` : '/api/v1/timetable/sessions',
          method: session ? 'PUT' : 'POST',
          body: {
            academicTermId: term.id,
            sectionId: Number(v.sectionId),
            subjectId: Number(v.subjectId),
            facultyId: Number(v.facultyId),
            roomId: Number(v.roomId),
            dayOfWeek: Number(v.dayOfWeek),
            startTime: v.startTime,
            endTime: v.endTime,
            effectiveStartDate: v.effectiveStartDate,
            effectiveEndDate: v.effectiveEndDate,
            status: v.status,
            sessionType: v.sessionType,
          },
        })
      }
    />
  );
}
