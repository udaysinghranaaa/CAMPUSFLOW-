import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '../../services/apiClient';
import { renderWorkspace } from '../../test/render';
import { department, failure, json, me, page, student, tokens } from '../../test/fixtures';
import { ManagerPage } from './ManagerPage';
import { EntityForm } from './EntityForm';
import { DashboardPage } from '../dashboard/DashboardPage';
import { DirectoryPage } from '../directory/DirectoryPage';
import type { Department } from '../../types/api';
let departments: Department[] = [];
let reads = 0;
let deleteConflict = false;
let loading = false;
let release!: (response: Response) => void;
beforeEach(async () => {
  api.endSession(null);
  departments = [department];
  reads = 0;
  deleteConflict = false;
  loading = false;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input));
      const method = init?.method ?? 'GET';
      if (url.pathname.endsWith('/login')) return json(tokens());
      if (url.pathname.endsWith('/auth/me')) return json(me());
      if (url.pathname === '/api/v1/departments' && method === 'POST') {
        const body = JSON.parse(String(init?.body));
        const row = { ...department, ...body, id: 2 };
        departments.push(row);
        return json(row, 201);
      }
      if (url.pathname === '/api/v1/departments/1' && method === 'DELETE')
        return failure(
          deleteConflict ? 409 : 500,
          'Department has faculty records; reassign or delete them first.',
        );
      if (url.pathname === '/api/v1/departments/1') return json(department);
      if (url.pathname === '/api/v1/departments') {
        reads++;
        if (loading)
          return await new Promise<Response>((resolve) => {
            release = resolve;
          });
        return json(page(departments));
      }
      if (url.pathname === '/api/v1/students') return json(page([student]));
      if (url.pathname === '/api/v1/directory/faculty')
        return json(
          page([
            {
              id: 1,
              fullName: 'Permitted Faculty',
              designation: 'Lecturer',
              departmentCode: 'CSE',
              active: true,
            },
          ]),
        );
      return json(page([]));
    }),
  );
  await api.login('member@example.edu', 'fixture-password');
});
describe('real contract management UI against mocked HTTP', () => {
  it('loads admin records and displays server totals', async () => {
    renderWorkspace(<ManagerPage resource="departments" />);
    expect(await screen.findByText('Computer Science')).toBeVisible();
    expect(screen.getByText('1 records · Page 1 of 1')).toBeVisible();
  });
  it('shows loading state before the response and empty state afterward', async () => {
    loading = true;
    renderWorkspace(<ManagerPage resource="departments" />);
    expect(await screen.findByRole('status')).toHaveTextContent('Loading departments');
    await waitFor(() => expect(release).toBeDefined());
    release(json(page([])));
    expect(await screen.findByText('No matching records')).toBeVisible();
  });
  it('creates through the POST endpoint, shows feedback and invalidates the list query', async () => {
    renderWorkspace(<ManagerPage resource="departments" />);
    await screen.findByText('Computer Science');
    const before = reads;
    await userEvent.click(screen.getByRole('button', { name: 'Add department' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('Department name'), 'Mathematics');
    await userEvent.type(within(dialog).getByLabelText('Department code'), 'MATH');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create record' }));
    expect(await screen.findByText('Department created.')).toBeVisible();
    expect(await screen.findByText('Mathematics')).toBeVisible();
    expect(reads).toBeGreaterThan(before);
    const request = vi
      .mocked(fetch)
      .mock.calls.find(
        ([, init]) => init?.method === 'POST' && String(init.body).includes('Mathematics'),
      )!;
    expect(JSON.parse(String(request[1]?.body))).toEqual({
      name: 'Mathematics',
      code: 'MATH',
      description: null,
    });
  });
  it('validates create forms before sending mutations', async () => {
    renderWorkspace(<ManagerPage resource="departments" />);
    await screen.findByText('Computer Science');
    await userEvent.click(screen.getByRole('button', { name: 'Add department' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create record' }));
    expect(await screen.findAllByText('This field is required.')).toHaveLength(2);
    expect(departments).toHaveLength(1);
  });
  it('keeps a dependency-conflicted record and reports the backend error after confirmation', async () => {
    deleteConflict = true;
    renderWorkspace(<ManagerPage resource="departments" />);
    await screen.findByText('Computer Science');
    await userEvent.click(screen.getByRole('button', { name: 'Delete Computer Science' }));
    expect(screen.getByRole('dialog')).toBeVisible();
    expect(vi.mocked(fetch).mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: 'Delete record' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Department has faculty records');
    expect(screen.getByRole('dialog')).toBeVisible();
    expect(departments).toHaveLength(1);
  });
  it('keeps enrollment readonly and excludes student deletion actions', async () => {
    const first = renderWorkspace(
      <EntityForm resource="students" record={student} onClose={() => {}} />,
    );
    expect(screen.getByLabelText('Enrollment number')).toHaveAttribute('readonly');
    first.unmount();
    renderWorkspace(<ManagerPage resource="students" />);
    expect(await screen.findByText('Test Student')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Delete Test Student' })).not.toBeInTheDocument();
  });
  it('uses server counts without fabricated dashboard statistics', async () => {
    renderWorkspace(<DashboardPage />);
    expect(await screen.findByText('Departments')).toBeVisible();
    await waitFor(() =>
      expect(
        within(screen.getByRole('link', { name: /Departments/ })).getByText('1'),
      ).toBeVisible(),
    );
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });
  it('only shows permitted directory fields', async () => {
    renderWorkspace(<DirectoryPage />);
    expect(await screen.findByText('Permitted Faculty')).toBeVisible();
    expect(screen.queryByText('Employee code')).not.toBeInTheDocument();
    expect(screen.queryByText('faculty@example.edu')).not.toBeInTheDocument();
  });
});
