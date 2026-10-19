import { useQueries } from '@tanstack/react-query';
import { Link } from 'react-router';
import { ShieldCheck, UserRound } from 'lucide-react';
import { useAuth } from '../auth/authContext';
import { useOwnProfile } from './useOwnProfile';
import { resources } from '../../services/resources';
import type { OwnProfile } from '../../types/api';
import { ErrorState, LoadingSkeleton, PageHeader, StatusBadge } from '../../components/ui/feedback';

function AcademicContext({ student }: { student: NonNullable<OwnProfile['student']> }) {
  const queries = useQueries({
    queries: [
      {
        queryKey: ['departments', 'detail', student.departmentId],
        queryFn: ({ signal }: { signal: AbortSignal }) =>
          resources.get('departments', student.departmentId, signal),
      },
      {
        queryKey: ['batches', 'detail', student.batchId],
        queryFn: ({ signal }: { signal: AbortSignal }) =>
          resources.get('batches', student.batchId, signal),
      },
      {
        queryKey: ['sections', 'detail', student.sectionId],
        queryFn: ({ signal }: { signal: AbortSignal }) =>
          resources.get('sections', student.sectionId!, signal),
        enabled: student.sectionId !== null,
      },
    ],
  });
  return (
    <section className="panel">
      <h2>Academic home</h2>
      <dl className="details-grid">
        {['Department', 'Batch', 'Section'].map((label, index) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>
              {index === 2 && student.sectionId === null
                ? 'Not assigned'
                : queries[index].isPending
                  ? 'Loading…'
                  : queries[index].isError
                    ? 'Unavailable'
                    : (queries[index].data?.name ?? '—')}
            </dd>
          </div>
        ))}
      </dl>
      {queries.some((q) => q.isError) && (
        <p className="form-error" role="alert">
          Some academic details could not be loaded.{' '}
          <button
            className="text-button"
            onClick={() =>
              queries.forEach((q) => {
                if (q.isError) void q.refetch();
              })
            }
          >
            Retry
          </button>
        </p>
      )}
    </section>
  );
}
export function ProfilePage() {
  const { user } = useAuth();
  const query = useOwnProfile();
  if (query.isPending) return <LoadingSkeleton label="Loading your profile…" />;
  if (query.isError) return <ErrorState error={query.error} retry={() => void query.refetch()} />;
  const profile = query.data;
  const person = profile.student ?? profile.faculty;
  const fields = profile.student
    ? [
        ['Enrollment number', profile.student.enrollmentNumber],
        ['Roll number', profile.student.rollNumber],
        ['Email', profile.student.email],
        ['Phone', profile.student.phoneNumber],
        ['Semester', profile.student.currentSemester],
        ['Admission year', profile.student.admissionYear],
      ]
    : profile.faculty
      ? [
          ['Employee code', profile.faculty.employeeCode],
          ['Email', profile.faculty.email],
          ['Designation', profile.faculty.designation],
          ['Department', profile.faculty.departmentCode],
        ]
      : [];
  return (
    <>
      <PageHeader
        eyebrow="YOUR CAMPUS IDENTITY"
        title="My profile"
        description="Your account and your own academic information."
      />
      <section className="panel profile-card">
        <span className="large-avatar">
          <UserRound size={36} />
        </span>
        <div>
          <span className="eyebrow">{user!.account.role} PORTAL</span>
          <h2>{person?.fullName ?? 'Campus administrator'}</h2>
          <p className="muted">{profile.account.email}</p>
        </div>
        <StatusBadge value={profile.account.enabled ? 'Enabled' : 'Disabled'} />
      </section>
      <div className="profile-columns">
        <section className="panel">
          <h2>{person ? 'Academic profile' : 'Account information'}</h2>
          {person ? (
            <dl className="details-grid">
              {fields.map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value ?? 'Not provided'}</dd>
                </div>
              ))}
            </dl>
          ) : (
            <p className="muted">
              Your administrator account is not linked to a faculty or student profile.
            </p>
          )}
          {person && <StatusBadge value={'status' in person ? person.status : person.active} />}
        </section>
        <section className="panel">
          <ShieldCheck className="accent" size={26} />
          <h2>Your account, protected</h2>
          <p className="muted">
            Profile information comes from your authenticated account. To correct academic details,
            contact your administrator.
          </p>
          <Link className="button secondary" to="/app/change-password">
            Change password
          </Link>
        </section>
      </div>
      {profile.student && <AcademicContext student={profile.student} />}
    </>
  );
}
