import { describe, it, expect } from 'vitest';
import { definitions, defaults } from './definitions';
import { changePasswordSchema, loginSchema } from '../auth/schemas';
import { student } from '../../test/fixtures';
describe('backend-aligned validation and write contracts', () => {
  it('validates login without imposing new-password rules on existing passwords', () => {
    expect(loginSchema.safeParse({ email: 'bad', password: '' }).success).toBe(false);
    expect(
      loginSchema.safeParse({ email: 'member@example.edu', password: 'existing' }).success,
    ).toBe(true);
  });
  it('enforces UTF-8 password byte limits and confirmation', () => {
    expect(
      changePasswordSchema.safeParse({
        currentPassword: 'old',
        newPassword: '😀'.repeat(20),
        confirmPassword: '😀'.repeat(20),
      }).success,
    ).toBe(false);
    expect(
      changePasswordSchema.safeParse({
        currentPassword: 'old',
        newPassword: 'a-secure-new-password',
        confirmPassword: 'different',
      }).success,
    ).toBe(false);
  });
  it('rejects end-year equal to start-year', () => {
    expect(
      definitions.batches.schema.safeParse({
        name: 'Batch',
        departmentId: '1',
        startYear: '2026',
        endYear: '2026',
        active: 'true',
      }).success,
    ).toBe(false);
  });
  it('rejects public ADMIN provisioning and mismatched profile links', () => {
    expect(
      definitions.accounts.schema.safeParse({
        email: 'member@example.edu',
        initialPassword: 'a-secure-password',
        role: 'ADMIN',
        facultyId: '',
        studentId: '',
      }).success,
    ).toBe(false);
    expect(
      definitions.accounts.schema.safeParse({
        email: 'member@example.edu',
        initialPassword: 'a-secure-password',
        role: 'FACULTY',
        facultyId: '',
        studentId: '1',
      }).success,
    ).toBe(false);
  });
  it('sends only the selected account profile link', () => {
    expect(
      definitions.accounts.payload({
        email: 'member@example.edu',
        initialPassword: 'a-secure-password',
        role: 'FACULTY',
        facultyId: '2',
        studentId: '9',
      }),
    ).toMatchObject({ role: 'FACULTY', facultyId: 2, studentId: null });
  });
  it('omits server-owned fields when editing a student', () => {
    const payload = definitions.students.payload(defaults('students', student));
    expect(payload).toMatchObject({ enrollmentNumber: student.enrollmentNumber, sectionId: null });
    expect(payload).not.toHaveProperty('status');
    expect(payload).not.toHaveProperty('id');
    expect(payload).not.toHaveProperty('createdAt');
  });
  it('accepts valid special-character names without weakening identifier normalization on the server', () => {
    const data = defaults('students', student);
    data.fullName = 'Literal_%! Student';
    expect(definitions.students.schema.safeParse(data).success).toBe(true);
  });
  it('rejects invalid phone syntax and nonpositive semesters', () => {
    const data = defaults('students', student);
    expect(
      definitions.students.schema.safeParse({ ...data, phoneNumber: 'bad<script>' }).success,
    ).toBe(false);
    expect(definitions.students.schema.safeParse({ ...data, currentSemester: '0' }).success).toBe(
      false,
    );
  });
});
