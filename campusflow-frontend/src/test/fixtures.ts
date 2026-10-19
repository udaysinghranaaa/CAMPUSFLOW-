import type { OwnProfile, Role, Page, Department, Student } from '../types/api';
export const audit = {
  id: 1,
  createdAt: '2026-10-19T00:00:00Z',
  updatedAt: '2026-10-19T00:00:00Z',
};
export const department: Department = {
  ...audit,
  name: 'Computer Science',
  code: 'CSE',
  description: 'Computing and ideas',
};
export const student: Student = {
  ...audit,
  enrollmentNumber: 'ENR-2026-1',
  rollNumber: 'R-1',
  fullName: 'Test Student',
  email: 'student@example.edu',
  phoneNumber: null,
  departmentId: 1,
  batchId: 1,
  sectionId: null,
  currentSemester: 1,
  admissionYear: 2026,
  status: 'ACTIVE',
};
export const me = (role: Role = 'ADMIN'): OwnProfile => ({
  account: {
    ...audit,
    email: 'test@example.edu',
    role,
    enabled: true,
    studentId: role === 'STUDENT' ? 1 : null,
    facultyId: role === 'FACULTY' ? 1 : null,
  },
  student: role === 'STUDENT' ? student : null,
  faculty:
    role === 'FACULTY'
      ? {
          ...audit,
          fullName: 'Test Faculty',
          email: 'faculty@example.edu',
          employeeCode: 'F1',
          designation: 'Lecturer',
          departmentId: 1,
          departmentCode: 'CSE',
          active: true,
        }
      : null,
});
export const tokens = (suffix = 'one', expiresIn = 900) => ({
  tokenType: 'Bearer',
  accessToken: `fixture-access-${suffix}`,
  expiresIn,
  refreshToken: (suffix === 'one' ? 'a' : 'b').repeat(43),
});
export const page = <T>(content: T[], totalElements = content.length): Page<T> => ({
  content,
  page: 0,
  size: 10,
  totalElements,
  totalPages: Math.ceil(totalElements / 10),
  last: true,
});
export const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
export const noContent = () => new Response(null, { status: 204 });
export const failure = (status: number, message = 'Request denied.') =>
  json(
    {
      timestamp: audit.createdAt,
      status,
      error: 'Error',
      message,
      path: '/api/test',
      fieldErrors: {},
    },
    status,
  );
