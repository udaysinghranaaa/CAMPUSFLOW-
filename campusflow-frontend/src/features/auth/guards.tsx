import { Navigate, Outlet } from 'react-router';
import type { Role } from '../../types/api';
import { useAuth } from './authContext';
import { roleHome } from '../../services/apiClient';
import { LoadingSkeleton } from '../../components/ui/feedback';
export function AuthGuard() {
  const { status } = useAuth();
  return status === 'restoring' ? (
    <LoadingSkeleton label="Checking your session…" />
  ) : status === 'authenticated' ? (
    <Outlet />
  ) : (
    <Navigate to="/login" replace />
  );
}
export function GuestGuard() {
  const { status, user } = useAuth();
  return status === 'restoring' ? (
    <LoadingSkeleton label="Checking your session…" />
  ) : user ? (
    <Navigate to={roleHome(user.account.role)} replace />
  ) : (
    <Outlet />
  );
}
export function RoleGuard({ role }: { role: Role }) {
  const { user } = useAuth();
  return user?.account.role === role ? <Outlet /> : <Navigate to="/unauthorized" replace />;
}
export function HomeRedirect() {
  const { user } = useAuth();
  return <Navigate to={user ? roleHome(user.account.role) : '/login'} replace />;
}
