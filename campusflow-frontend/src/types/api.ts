export type Role = 'ADMIN' | 'FACULTY' | 'STUDENT';
export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'GRADUATED' | 'SUSPENDED';
export interface Audit {
  id: number;
  createdAt: string;
  updatedAt: string;
}
export interface Department extends Audit {
  name: string;
  code: string;
  description: string | null;
}
export interface Faculty extends Audit {
  fullName: string;
  email: string;
  employeeCode: string;
  designation: string | null;
  departmentId: number;
  departmentCode: string;
  active: boolean;
}
export interface Student extends Audit {
  enrollmentNumber: string;
  rollNumber: string;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  departmentId: number;
  batchId: number;
  sectionId: number | null;
  currentSemester: number;
  admissionYear: number;
  status: StudentStatus;
}
export interface Batch extends Audit {
  name: string;
  startYear: number;
  endYear: number;
  departmentId: number;
  active: boolean;
}
export interface Section extends Audit {
  name: string;
  batchId: number;
  capacity: number | null;
  active: boolean;
}
export interface Account extends Audit {
  email: string;
  role: Role;
  enabled: boolean;
  studentId: number | null;
  facultyId: number | null;
}
export interface OwnProfile {
  account: Account;
  student: Student | null;
  faculty: Faculty | null;
}
export interface DirectoryEntry {
  id: number;
  fullName: string;
  designation: string | null;
  departmentCode: string;
  active: boolean;
}
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
export interface Entities {
  departments: Department;
  faculty: Faculty;
  students: Student;
  batches: Batch;
  sections: Section;
  accounts: Account;
}
export type Resource = keyof Entities;
export type Entity = Entities[Resource];
export interface DepartmentRequest {
  name: string;
  code: string;
  description: string | null;
}
export interface FacultyRequest {
  fullName: string;
  email: string;
  employeeCode: string;
  designation: string | null;
  departmentId: number;
}
export interface StudentRequest {
  enrollmentNumber: string;
  rollNumber: string;
  fullName: string;
  email: string;
  phoneNumber: string | null;
  departmentId: number;
  batchId: number;
  sectionId: number | null;
  currentSemester: number;
  admissionYear: number;
}
export interface BatchRequest {
  name: string;
  startYear: number;
  endYear: number;
  departmentId: number;
  active: boolean;
}
export interface SectionRequest {
  name: string;
  batchId: number;
  capacity: number | null;
  active: boolean;
}
export interface ProvisionRequest {
  email: string;
  initialPassword: string;
  role: 'STUDENT' | 'FACULTY';
  studentId: number | null;
  facultyId: number | null;
}
export interface Requests {
  departments: DepartmentRequest;
  faculty: FacultyRequest;
  students: StudentRequest;
  batches: BatchRequest;
  sections: SectionRequest;
  accounts: ProvisionRequest;
}
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: Record<string, string>;
}

export interface Subject extends Audit {
  code: string;
  name: string;
  departmentId: number;
  description: string | null;
  active: boolean;
}
export interface Room extends Audit {
  code: string;
  name: string;
  building: string;
  floor: string | null;
  type: 'CLASSROOM' | 'LAB' | 'SEMINAR';
  capacity: number;
  active: boolean;
}
export interface SubjectRequest {
  code: string;
  name: string;
  departmentId: number;
  description: string | null;
  active: boolean;
}
export interface RoomRequest {
  code: string;
  name: string;
  building: string;
  floor: string | null;
  type: 'CLASSROOM' | 'LAB' | 'SEMINAR';
  capacity: number;
  active: boolean;
}
export interface Entities {
  subjects: Subject;
  rooms: Room;
}
export interface Requests {
  subjects: SubjectRequest;
  rooms: RoomRequest;
}
