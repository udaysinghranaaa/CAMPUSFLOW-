import { z } from 'zod';
import type { Resource, Requests, Entity } from '../../types/api';
import { textValue } from '../../services/resources';
import { passwordSchema } from '../auth/schemas';
export interface Field {
  name: string;
  label: string;
  type?: 'text' | 'email' | 'password' | 'number' | 'textarea' | 'select' | 'relation';
  resource?: Resource;
  options?: { value: string; label: string }[];
  optional?: boolean;
  immutable?: boolean;
  default?: string;
  when?: { field: string; value: string };
}
interface Definition<K extends Resource> {
  title: string;
  singular: string;
  description: string;
  columns: { key: string; label: string }[];
  fields: Field[];
  filters: Field[];
  schema: z.ZodType<Record<string, string>, Record<string, string>>;
  payload: (values: Record<string, string>) => Requests[K];
}
const required = (max: number) =>
  z.string().trim().min(1, 'This field is required.').max(max, `Use at most ${max} characters.`);
const integer = z
  .string()
  .regex(/^[1-9][0-9]*$/, 'Enter a positive whole number.')
  .refine((value) => Number.isSafeInteger(Number(value)), 'Number is too large.');
const optionalInteger = z.union([z.literal(''), integer]);
const year = integer.refine(
  (value) => Number(value) >= 1900 && Number(value) <= 9999,
  'Use a year between 1900 and 9999.',
);
const email = z.email('Enter a valid email address.').max(254);
const active = z.enum(['true', 'false']);
const department: Field = {
  name: 'departmentId',
  label: 'Department',
  type: 'relation',
  resource: 'departments',
};
const batch: Field = {
  name: 'batchId',
  label: 'Academic batch',
  type: 'relation',
  resource: 'batches',
};
const section: Field = {
  name: 'sectionId',
  label: 'Section',
  type: 'relation',
  resource: 'sections',
  optional: true,
};
const activeField: Field = {
  name: 'active',
  label: 'Status',
  type: 'select',
  options: [
    { value: 'true', label: 'Active' },
    { value: 'false', label: 'Inactive' },
  ],
  default: 'true',
};
const col = (key: string, label: string) => ({ key, label });
export const definitions: { [K in Resource]: Definition<K> } = {
  departments: {
    title: 'Departments',
    singular: 'department',
    description: 'The foundations of your campus, in one place.',
    columns: [col('name', 'Department'), col('code', 'Code'), col('description', 'Description')],
    fields: [
      { name: 'name', label: 'Department name' },
      { name: 'code', label: 'Department code' },
      { name: 'description', label: 'Description', type: 'textarea', optional: true },
    ],
    filters: [{ name: 'code', label: 'Department code' }],
    schema: z.object({
      name: required(120),
      code: required(20),
      description: z.string().max(1000),
    }),
    payload: (v) => ({
      name: v.name.trim(),
      code: v.code.trim(),
      description: v.description.trim() || null,
    }),
  },
  faculty: {
    title: 'Faculty',
    singular: 'faculty member',
    description: 'Support the people who make learning happen.',
    columns: [
      col('fullName', 'Faculty member'),
      col('employeeCode', 'Employee code'),
      col('departmentCode', 'Department'),
      col('designation', 'Designation'),
      col('active', 'Status'),
    ],
    fields: [
      { name: 'fullName', label: 'Full name' },
      { name: 'email', label: 'Email address', type: 'email' },
      { name: 'employeeCode', label: 'Employee code' },
      { name: 'designation', label: 'Designation', optional: true },
      department,
    ],
    filters: [
      { name: 'name', label: 'Faculty name' },
      department,
      { ...activeField, optional: true, default: '' },
    ],
    schema: z.object({
      fullName: required(150),
      email,
      employeeCode: required(30),
      designation: z.string().max(100),
      departmentId: integer,
    }),
    payload: (v) => ({
      fullName: v.fullName.trim(),
      email: v.email.trim(),
      employeeCode: v.employeeCode.trim(),
      designation: v.designation.trim() || null,
      departmentId: Number(v.departmentId),
    }),
  },
  students: {
    title: 'Students',
    singular: 'student',
    description: 'Every student. Every step of their academic journey.',
    columns: [
      col('fullName', 'Student'),
      col('enrollmentNumber', 'Enrollment'),
      col('currentSemester', 'Semester'),
      col('status', 'Status'),
    ],
    fields: [
      { name: 'enrollmentNumber', label: 'Enrollment number', immutable: true },
      { name: 'rollNumber', label: 'Roll number' },
      { name: 'fullName', label: 'Full name' },
      { name: 'email', label: 'Email address', type: 'email' },
      { name: 'phoneNumber', label: 'Phone number', optional: true },
      department,
      batch,
      section,
      { name: 'currentSemester', label: 'Current semester', type: 'number', default: '1' },
      { name: 'admissionYear', label: 'Admission year', type: 'number' },
    ],
    filters: [
      { name: 'enrollmentNumber', label: 'Enrollment number' },
      { name: 'name', label: 'Student name' },
      department,
      batch,
      section,
      { name: 'semester', label: 'Semester', type: 'number' },
      {
        name: 'status',
        label: 'Student status',
        type: 'select',
        options: ['ACTIVE', 'INACTIVE', 'GRADUATED', 'SUSPENDED'].map((value) => ({
          value,
          label: value.toLowerCase(),
        })),
      },
    ],
    schema: z.object({
      enrollmentNumber: required(40),
      rollNumber: required(30),
      fullName: required(150),
      email,
      phoneNumber: z
        .string()
        .max(25)
        .regex(/^[+0-9() .-]*$/, 'Invalid phone number format.'),
      departmentId: integer,
      batchId: integer,
      sectionId: optionalInteger,
      currentSemester: integer,
      admissionYear: year,
    }),
    payload: (v) => ({
      enrollmentNumber: v.enrollmentNumber.trim(),
      rollNumber: v.rollNumber.trim(),
      fullName: v.fullName.trim(),
      email: v.email.trim(),
      phoneNumber: v.phoneNumber.trim() || null,
      departmentId: Number(v.departmentId),
      batchId: Number(v.batchId),
      sectionId: v.sectionId ? Number(v.sectionId) : null,
      currentSemester: Number(v.currentSemester),
      admissionYear: Number(v.admissionYear),
    }),
  },
  batches: {
    title: 'Academic batches',
    singular: 'batch',
    description: 'Organize cohorts and keep academic years connected.',
    columns: [
      col('name', 'Batch'),
      col('startYear', 'Start year'),
      col('endYear', 'End year'),
      col('departmentId', 'Department ID'),
      col('active', 'Status'),
    ],
    fields: [
      { name: 'name', label: 'Batch name' },
      department,
      { name: 'startYear', label: 'Start year', type: 'number' },
      { name: 'endYear', label: 'End year', type: 'number' },
      activeField,
    ],
    filters: [department],
    schema: z
      .object({
        name: required(150),
        departmentId: integer,
        startYear: year,
        endYear: year,
        active,
      })
      .refine((v) => Number(v.endYear) > Number(v.startYear), {
        path: ['endYear'],
        message: 'End year must be later than start year.',
      }),
    payload: (v) => ({
      name: v.name.trim(),
      departmentId: Number(v.departmentId),
      startYear: Number(v.startYear),
      endYear: Number(v.endYear),
      active: v.active === 'true',
    }),
  },
  sections: {
    title: 'Sections',
    singular: 'section',
    description: 'Give every cohort a little more structure.',
    columns: [
      col('name', 'Section'),
      col('batchId', 'Batch ID'),
      col('capacity', 'Capacity'),
      col('active', 'Status'),
    ],
    fields: [
      { name: 'name', label: 'Section name' },
      batch,
      { name: 'capacity', label: 'Capacity', type: 'number', optional: true },
      activeField,
    ],
    filters: [batch],
    schema: z.object({ name: required(30), batchId: integer, capacity: optionalInteger, active }),
    payload: (v) => ({
      name: v.name.trim(),
      batchId: Number(v.batchId),
      capacity: v.capacity ? Number(v.capacity) : null,
      active: v.active === 'true',
    }),
  },
  accounts: {
    title: 'Accounts',
    singular: 'account',
    description: 'The right access for the right people.',
    columns: [
      col('email', 'Account email'),
      col('role', 'Role'),
      col('enabled', 'Access'),
      col('studentId', 'Student ID'),
      col('facultyId', 'Faculty ID'),
    ],
    fields: [
      { name: 'email', label: 'Login email', type: 'email' },
      { name: 'initialPassword', label: 'Initial password', type: 'password' },
      {
        name: 'role',
        label: 'Account role',
        type: 'select',
        options: [
          { value: 'FACULTY', label: 'Faculty' },
          { value: 'STUDENT', label: 'Student' },
        ],
        default: 'FACULTY',
      },
      {
        name: 'facultyId',
        label: 'Faculty profile',
        type: 'relation',
        resource: 'faculty',
        when: { field: 'role', value: 'FACULTY' },
      },
      {
        name: 'studentId',
        label: 'Student profile',
        type: 'relation',
        resource: 'students',
        when: { field: 'role', value: 'STUDENT' },
      },
    ],
    filters: [],
    schema: z
      .object({
        email,
        initialPassword: passwordSchema,
        role: z.enum(['FACULTY', 'STUDENT']),
        facultyId: z.string(),
        studentId: z.string(),
      })
      .superRefine((v, ctx) => {
        const key = v.role === 'FACULTY' ? 'facultyId' : 'studentId';
        if (!integer.safeParse(v[key]).success)
          ctx.addIssue({ code: 'custom', path: [key], message: 'Select an existing profile.' });
      }),
    payload: (v) => ({
      email: v.email.trim(),
      initialPassword: v.initialPassword,
      role: v.role === 'FACULTY' ? 'FACULTY' : 'STUDENT',
      facultyId: v.role === 'FACULTY' ? Number(v.facultyId) : null,
      studentId: v.role === 'STUDENT' ? Number(v.studentId) : null,
    }),
  },
  subjects: {
    title: 'Subjects',
    singular: 'subject',
    description: 'Academic subjects and their teaching departments.',
    columns: [
      col('name', 'Subject'),
      col('code', 'Code'),
      col('departmentId', 'Department ID'),
      col('active', 'Status'),
    ],
    fields: [
      { name: 'code', label: 'Subject code' },
      { name: 'name', label: 'Subject name' },
      department,
      { name: 'description', label: 'Description', type: 'textarea', optional: true },
      activeField,
    ],
    filters: [
      { name: 'name', label: 'Subject name or code' },
      department,
      { ...activeField, optional: true, default: '' },
    ],
    schema: z.object({
      code: required(30),
      name: required(150),
      departmentId: integer,
      description: z.string().max(1000),
      active,
    }),
    payload: (v) => ({
      code: v.code,
      name: v.name,
      departmentId: Number(v.departmentId),
      description: v.description.trim() || null,
      active: v.active === 'true',
    }),
  },
  rooms: {
    title: 'Rooms & labs',
    singular: 'room',
    description: 'Teaching spaces, capacities and room metadata. Reservations are a future module.',
    columns: [
      col('code', 'Room code'),
      col('name', 'Room'),
      col('building', 'Building'),
      col('type', 'Type'),
      col('capacity', 'Capacity'),
      col('active', 'Status'),
    ],
    fields: [
      { name: 'code', label: 'Room code' },
      { name: 'name', label: 'Room name' },
      { name: 'building', label: 'Building' },
      { name: 'floor', label: 'Floor', optional: true },
      {
        name: 'type',
        label: 'Room type',
        type: 'select',
        default: 'CLASSROOM',
        options: ['CLASSROOM', 'LAB', 'SEMINAR'].map((value) => ({
          value,
          label: value.toLowerCase(),
        })),
      },
      { name: 'capacity', label: 'Capacity', type: 'number' },
      activeField,
    ],
    filters: [
      { name: 'name', label: 'Room name or code' },
      { name: 'building', label: 'Building' },
      {
        name: 'type',
        label: 'Room type',
        type: 'select',
        options: ['CLASSROOM', 'LAB', 'SEMINAR'].map((value) => ({
          value,
          label: value.toLowerCase(),
        })),
      },
      { ...activeField, optional: true, default: '' },
    ],
    schema: z.object({
      code: required(30),
      name: required(150),
      building: required(100),
      floor: z.string().max(30),
      type: z.enum(['CLASSROOM', 'LAB', 'SEMINAR']),
      capacity: integer,
      active,
    }),
    payload: (v) => ({
      code: v.code,
      name: v.name,
      building: v.building,
      floor: v.floor.trim() || null,
      type: v.type as 'CLASSROOM' | 'LAB' | 'SEMINAR',
      capacity: Number(v.capacity),
      active: v.active === 'true',
    }),
  },
};
export function defaults(resource: Resource, record?: Entity) {
  return Object.fromEntries(
    definitions[resource].fields.map((field) => [
      field.name,
      record ? textValue(record, field.name) : (field.default ?? ''),
    ]),
  );
}
