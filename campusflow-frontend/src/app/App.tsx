import { Component, Suspense, lazy } from 'react';
import type { ReactNode } from 'react';
import { Link, Route, Routes } from 'react-router';
import { ShieldAlert, MapPinOff } from 'lucide-react';
import { LoginPage } from '../features/auth/LoginPage';
import { AuthGuard, GuestGuard, HomeRedirect, RoleGuard } from '../features/auth/guards';
import { PasswordPage } from '../features/auth/PasswordPage';
import { AppShell } from '../components/layout/AppShell';
import { LoadingSkeleton } from '../components/ui/feedback';
import type { Resource } from '../types/api';
const BuilderPage = lazy(() =>
  import('../features/timetable/BuilderPage').then((m) => ({ default: m.BuilderPage })),
);
const TermsPage = lazy(() =>
  import('../features/timetable/TermsPage').then((m) => ({ default: m.TermsPage })),
);
const FinderPage = lazy(() =>
  import('../features/timetable/FinderPage').then((m) => ({ default: m.FinderPage })),
);
const OwnTimetablePage = lazy(() =>
  import('../features/timetable/OwnTimetablePage').then((m) => ({ default: m.OwnTimetablePage })),
);
const ManagerPage = lazy(() =>
  import('../features/management/ManagerPage').then((module) => ({ default: module.ManagerPage })),
);
const DashboardPage = lazy(() =>
  import('../features/dashboard/DashboardPage').then((module) => ({
    default: module.DashboardPage,
  })),
);
const PortalPage = lazy(() =>
  import('../features/dashboard/PortalPage').then((module) => ({ default: module.PortalPage })),
);
const ProfilePage = lazy(() =>
  import('../features/profile/ProfilePage').then((module) => ({ default: module.ProfilePage })),
);
const DirectoryPage = lazy(() =>
  import('../features/directory/DirectoryPage').then((module) => ({
    default: module.DirectoryPage,
  })),
);
function MissingPage({ denied = false }: { denied?: boolean }) {
  return (
    <div className="full-state">
      {denied ? <ShieldAlert size={44} /> : <MapPinOff size={44} />}
      <span className="eyebrow">{denied ? 'ACCESS RESTRICTED' : 'PAGE NOT FOUND'}</span>
      <h1>{denied ? 'This space belongs to another role.' : 'Looks like a different path.'}</h1>
      <p className="muted">
        {denied
          ? 'Your account doesn’t have access to this workspace.'
          : 'The page may have moved, or the address may be incorrect.'}
      </p>
      <Link className="button primary" to="/app">
        Go to my workspace
      </Link>
    </div>
  );
}
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch() {
    /* Never log potentially sensitive application state. */
  }
  render() {
    return this.state.failed ? (
      <div className="full-state">
        <h1>Something interrupted your workspace.</h1>
        <p>Your account is safe. Reload the page and sign in again.</p>
        <button className="button primary" onClick={() => window.location.reload()}>
          Reload CampusFlow
        </button>
      </div>
    ) : (
      this.props.children
    );
  }
}
export function App() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<LoadingSkeleton />}>
        <Routes>
          <Route element={<GuestGuard />}>
            <Route path="/login" element={<LoginPage />} />
          </Route>
          <Route path="/unauthorized" element={<MissingPage denied />} />
          <Route element={<AuthGuard />}>
            <Route element={<AppShell />}>
              <Route path="/app" element={<HomeRedirect />} />
              <Route path="/app/faculty-finder" element={<FinderPage />} />
              <Route path="/app/profile" element={<ProfilePage />} />
              <Route path="/app/change-password" element={<PasswordPage />} />
              <Route element={<RoleGuard role="ADMIN" />}>
                <Route path="/admin" element={<DashboardPage />} />
                <Route path="/admin/timetable" element={<BuilderPage />} />
                <Route path="/admin/terms" element={<TermsPage />} />
                {(
                  [
                    'departments',
                    'faculty',
                    'students',
                    'batches',
                    'sections',
                    'accounts',
                    'subjects',
                    'rooms',
                  ] as Resource[]
                ).map((resource) => (
                  <Route
                    key={resource}
                    path={`/admin/${resource}`}
                    element={<ManagerPage resource={resource} />}
                  />
                ))}
              </Route>
              <Route element={<RoleGuard role="FACULTY" />}>
                <Route path="/faculty" element={<PortalPage />} />
                <Route path="/faculty/timetable" element={<OwnTimetablePage />} />
                <Route path="/faculty/profile" element={<ProfilePage />} />
                <Route path="/faculty/directory" element={<DirectoryPage />} />
              </Route>
              <Route element={<RoleGuard role="STUDENT" />}>
                <Route path="/student" element={<PortalPage />} />
                <Route path="/student/timetable" element={<OwnTimetablePage />} />
                <Route path="/student/profile" element={<ProfilePage />} />
                <Route path="/student/faculty-directory" element={<DirectoryPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="*" element={<MissingPage />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
