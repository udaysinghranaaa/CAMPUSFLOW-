import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { LoginPage } from './LoginPage';
import { AuthGuard, GuestGuard, RoleGuard } from './guards';
import { AppShell } from '../../components/layout/AppShell';
import { renderWorkspace } from '../../test/render';
import { failure, json, me, noContent, tokens } from '../../test/fixtures';
import type { Role } from '../../types/api';
import { api } from '../../services/apiClient';
let role: Role = 'ADMIN';
beforeEach(() => {
  api.endSession(null);
  role = 'ADMIN';
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) =>
      String(input).endsWith('/login')
        ? json(tokens())
        : String(input).endsWith('/logout')
          ? noContent()
          : json(me(role)),
    ),
  );
});
function loginRoutes() {
  return (
    <Routes>
      <Route element={<GuestGuard />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route path="/admin" element={<h1>Admin destination</h1>} />
      <Route path="/faculty" element={<h1>Faculty destination</h1>} />
      <Route path="/student" element={<h1>Student destination</h1>} />
    </Routes>
  );
}
describe('login and navigation boundaries', () => {
  it('shows required-field validation without making a network request', async () => {
    renderWorkspace(loginRoutes());
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in to CampusFlow' }));
    expect(await screen.findByText('Enter a valid email address.')).toBeVisible();
    expect(screen.getByText('Enter your password.')).toBeVisible();
    expect(fetch).not.toHaveBeenCalled();
  });
  it.each(['ADMIN', 'FACULTY', 'STUDENT'] as Role[])(
    'redirects %s to its own role home after actual mocked HTTP login',
    async (nextRole) => {
      role = nextRole;
      renderWorkspace(loginRoutes());
      await userEvent.type(await screen.findByLabelText('Email address'), 'member@example.edu');
      await userEvent.type(screen.getByLabelText('Password'), 'fixture-password');
      await userEvent.click(screen.getByRole('button', { name: 'Sign in to CampusFlow' }));
      expect(
        await screen.findByRole('heading', {
          name: `${nextRole[0] + nextRole.slice(1).toLowerCase()} destination`,
        }),
      ).toBeVisible();
    },
  );
  it('handles invalid credentials without showing raw response data', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => failure(401, 'Authentication required or invalid credentials.')),
    );
    renderWorkspace(loginRoutes());
    await userEvent.type(await screen.findByLabelText('Email address'), 'member@example.edu');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in to CampusFlow' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Authentication required or invalid credentials.',
    );
    expect(api.getSnapshot().status).toBe('anonymous');
  });
  it('disables submit during login and shows a safe network error', async () => {
    let release!: (value: Response) => void;
    vi.stubGlobal(
      'fetch',
      vi.fn(
        () =>
          new Promise<Response>((resolve) => {
            release = resolve;
          }),
      ),
    );
    renderWorkspace(loginRoutes());
    fireEvent.change(await screen.findByLabelText('Email address'), {
      target: { value: 'member@example.edu' },
    });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'fixture-password' } });
    await userEvent.click(screen.getByRole('button', { name: 'Sign in to CampusFlow' }));
    expect(await screen.findByRole('button', { name: 'Signing in…' })).toBeDisabled();
    release(failure(503));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The server could not complete the request.',
    );
  });
  it('toggles password visibility with an accessible control', async () => {
    renderWorkspace(loginRoutes());
    const password = await screen.findByLabelText('Password');
    expect(password).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(password).toHaveAttribute('type', 'text');
    await userEvent.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(password).toHaveAttribute('type', 'password');
  });
  it('sends unauthenticated protected navigation to login', async () => {
    renderWorkspace(
      <Routes>
        <Route element={<AuthGuard />}>
          <Route path="/protected" element={<h1>Private</h1>} />
        </Route>
        <Route path="/login" element={<h1>Sign in required</h1>} />
      </Routes>,
      '/protected',
    );
    expect(await screen.findByText('Sign in required')).toBeVisible();
    expect(screen.queryByText('Private')).not.toBeInTheDocument();
  });
  it.each([
    ['STUDENT', '/admin'],
    ['FACULTY', '/admin'],
    ['STUDENT', '/faculty'],
  ] as const)('blocks %s from %s', async (testRole, path) => {
    role = testRole;
    await api.login('member@example.edu', 'fixture-password');
    renderWorkspace(
      <Routes>
        <Route element={<AuthGuard />}>
          <Route element={<RoleGuard role={path === '/admin' ? 'ADMIN' : 'FACULTY'} />}>
            <Route path={path} element={<h1>Forbidden workspace</h1>} />
          </Route>
        </Route>
        <Route path="/unauthorized" element={<h1>Access restricted</h1>} />
      </Routes>,
      path,
    );
    expect(await screen.findByText('Access restricted')).toBeVisible();
    expect(screen.queryByText('Forbidden workspace')).not.toBeInTheDocument();
  });
  it('allows the authorized role through its guard', async () => {
    await api.login('member@example.edu', 'fixture-password');
    renderWorkspace(
      <Routes>
        <Route element={<AuthGuard />}>
          <Route element={<RoleGuard role="ADMIN" />}>
            <Route path="/admin" element={<h1>Authorized</h1>} />
          </Route>
        </Route>
      </Routes>,
      '/admin',
    );
    expect(await screen.findByText('Authorized')).toBeVisible();
  });
  it('logs out from the shell and clears private query cache', async () => {
    await api.login('member@example.edu', 'fixture-password');
    const { cache } = renderWorkspace(
      <Routes>
        <Route element={<AuthGuard />}>
          <Route element={<AppShell />}>
            <Route path="/admin" element={<h1>Protected content</h1>} />
          </Route>
        </Route>
        <Route path="/login" element={<h1>Signed out</h1>} />
      </Routes>,
      '/admin',
    );
    cache.setQueryData(['private'], { name: 'private fixture' });
    await userEvent.click(screen.getAllByRole('button', { name: 'Sign out' })[0]);
    expect(await screen.findByText('Signed out')).toBeVisible();
    await waitFor(() => expect(cache.getQueryData(['private'])).toBeUndefined());
  });
});
