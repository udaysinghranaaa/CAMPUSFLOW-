import type { ScheduleField } from './ScheduleForm';
import { z } from 'zod';
import { ScheduleForm, dateField, positiveId, timeField } from './ScheduleForm';
import type { Session, Term } from './types';
import { useScheduleMutation } from './hooks';
const schema = z
  .object({
    date: dateField,
    type: z.enum(['CANCELLED', 'RESCHEDULED']),
    startTime: z.string(),
    endTime: z.string(),
    facultyId: z.string(),
    roomId: z.string(),
    reason: z.string().max(500),
  })
  .superRefine((v, ctx) => {
    if (v.type === 'RESCHEDULED') {
      for (const key of ['facultyId', 'roomId'] as const)
        if (!positiveId.safeParse(v[key]).success)
          ctx.addIssue({ code: 'custom', path: [key], message: 'Choose a replacement.' });
      if (
        !timeField.safeParse(v.startTime).success ||
        !timeField.safeParse(v.endTime).success ||
        v.startTime >= v.endTime
      )
        ctx.addIssue({ code: 'custom', path: ['endTime'], message: 'Use ordered valid times.' });
    }
  });
export function ExceptionEditor({
  term,
  session,
  onClose,
}: {
  term: Term;
  session: Session;
  onClose: () => void;
}) {
  const write = useScheduleMutation();
  return (
    <ScheduleForm
      title="Change one occurrence"
      schema={schema}
      defaults={{
        date: session.effectiveStartDate,
        type: 'CANCELLED',
        startTime: session.startTime.slice(0, 5),
        endTime: session.endTime.slice(0, 5),
        facultyId: String(session.facultyId),
        roomId: String(session.roomId),
        reason: '',
      }}
      onClose={onClose}
      fields={(v): ScheduleField[] => [
        { name: 'date', label: 'Original occurrence date', type: 'date' },
        {
          name: 'type',
          label: 'Exception type',
          type: 'select',
          options: ['CANCELLED', 'RESCHEDULED'],
        },
        ...(v.type === 'RESCHEDULED'
          ? [
              {
                name: 'facultyId',
                label: 'Replacement faculty',
                type: 'relation' as const,
                resource: 'faculty' as const,
                params: { departmentId: String(term.departmentId), active: 'true' },
              },
              {
                name: 'roomId',
                label: 'Replacement room',
                type: 'relation' as const,
                resource: 'rooms' as const,
                params: { active: 'true' },
              },
              { name: 'startTime', label: 'Replacement start', type: 'time' as const },
              { name: 'endTime', label: 'Replacement end', type: 'time' as const },
            ]
          : []),
        { name: 'reason', label: 'Administrative reason', optional: true },
      ]}
      onSubmit={(v) =>
        write.mutateAsync({
          path: `/api/v1/timetable/sessions/${session.id}/exceptions`,
          method: 'POST',
          body: {
            exceptionDate: v.date,
            type: v.type,
            startTime: v.type === 'RESCHEDULED' ? v.startTime : null,
            endTime: v.type === 'RESCHEDULED' ? v.endTime : null,
            facultyId: v.type === 'RESCHEDULED' ? Number(v.facultyId) : null,
            roomId: v.type === 'RESCHEDULED' ? Number(v.roomId) : null,
            reason: v.reason || null,
          },
        })
      }
    />
  );
}
