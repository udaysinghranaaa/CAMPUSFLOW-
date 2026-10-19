import type { Role } from '../../types/api';
export type Availability = 'FREE' | 'BUSY' | 'UNKNOWN' | 'UNAVAILABLE';
export interface Term {
  id: number;
  name: string;
  departmentId: number;
  semester: number;
  startDate: string;
  endDate: string;
  active: boolean;
}
export interface Session {
  id: number;
  academicTermId: number;
  sectionId: number;
  sectionName: string;
  subjectId: number;
  subjectName: string;
  facultyId: number;
  facultyName: string;
  roomId: number;
  roomCode: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  effectiveStartDate: string;
  effectiveEndDate: string;
  status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED';
  sessionType: 'LECTURE' | 'PRACTICAL' | 'TUTORIAL';
}
export interface Occurrence {
  sessionId: number;
  date: string;
  startTime: string;
  endTime: string;
  subjectName: string;
  facultyName: string;
  roomCode: string;
  sectionId: number;
  sectionName: string;
  sessionType: string;
  status: 'DRAFT' | 'PUBLISHED';
}
export interface Weekly {
  weekStart: string;
  timezone: 'Asia/Kolkata';
  sessions: Occurrence[];
}
export interface FoundFaculty {
  id: number;
  fullName: string;
  departmentCode: string;
  designation: string | null;
  subjects: string[];
  state: Availability;
  explanation: string;
  date: string;
  startTime: string;
  endTime: string;
}
export interface Unavailable {
  id: number;
  facultyId: number;
  date: string;
  startTime: string;
  endTime: string;
  reason: string | null;
  active: boolean;
}
export const schedulePath = (role: Role) =>
  role === 'ADMIN'
    ? '/admin/timetable'
    : role === 'FACULTY'
      ? '/faculty/timetable'
      : '/student/timetable';
