import { expect, test } from '@playwright/test';
import type { Page as BrowserPage } from '@playwright/test';
import { audit, department, me, page as paged, student, tokens } from '../src/test/fixtures';
import type { Role } from '../src/types/api';
async function mockApi(browser: BrowserPage, role: Role) {
  const departments = [department];
  const faculty = {
    ...audit,
    fullName: 'Test Faculty',
    email: 'faculty@example.edu',
    employeeCode: 'F1',
    designation: 'Lecturer',
    departmentId: 1,
    departmentCode: 'CSE',
    active: true,
  };
  const batch = {
    ...audit,
    name: 'CSE 2026',
    departmentId: 1,
    startYear: 2026,
    endYear: 2030,
    active: true,
  };
  const section = { ...audit, name: 'A', batchId: 1, capacity: 60, active: true };
  await browser.route('http://localhost:8080/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    const headers = {
      'access-control-allow-origin': new URL(browser.url()).origin,
      'access-control-allow-headers': 'Authorization,Content-Type',
      'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    };
    const respond = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        headers,
        contentType: 'application/json',
        body: JSON.stringify(body),
      });
    if (method === 'OPTIONS') return route.fulfill({ status: 204, headers });
    if (path.endsWith('/auth/login') || path.endsWith('/auth/refresh')) return respond(tokens());
    if (path.endsWith('/auth/me')) return respond(me(role));
    if (path.endsWith('/auth/logout')) return route.fulfill({ status: 204, headers });
    if (path === '/api/v1/directory/faculty')
      return respond(
        paged([
          {
            id: 1,
            fullName: faculty.fullName,
            designation: faculty.designation,
            departmentCode: 'CSE',
            active: true,
          },
        ]),
      );
    if (path === '/api/v1/departments' && method === 'POST') {
      const created = { ...department, ...request.postDataJSON(), id: 2 };
      departments.push(created);
      return respond(created, 201);
    }
    if (path === '/api/v1/departments') return respond(paged(departments));
    if (path.startsWith('/api/v1/departments/')) return respond(department);
    if (path === '/api/v1/faculty') return respond(paged([faculty]));
    if (path === '/api/v1/students') return respond(paged([student]));
    if (path === '/api/v1/batches') return respond(paged([batch]));
    if (path === '/api/v1/batches/1') return respond(batch);
    if (path === '/api/v1/sections') return respond(paged([section]));
    if (path === '/api/v1/admin/accounts') return respond(paged([me(role).account]));
    return respond({ status: 404, message: 'Fixture not found.', fieldErrors: {} }, 404);
  });
}
async function navigate(browser: BrowserPage, label: string) {
  const toggle = browser.getByRole('button', { name: 'Open navigation' });
  if (await toggle.isVisible()) await toggle.click();
  await browser
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('link', { name: label, exact: true })
    .click();
}
async function noOverflow(browser: BrowserPage) {
  expect(
    await browser.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBe(true);
}
for (const width of [375, 768, 1280]) {
  test(`ADMIN workspace and management at ${width}px`, async ({ page: browser }) => {
    await browser.setViewportSize({ width, height: 900 });
    await mockApi(browser, 'ADMIN');
    await browser.goto('/login');
    await expect(browser.getByRole('heading', { name: 'A good day starts here.' })).toBeVisible();
    await noOverflow(browser);
    await browser.getByLabel('Email address').fill('test@example.edu');
    await browser.getByLabel('Password', { exact: true }).fill('fixture-password');
    await browser.getByRole('button', { name: 'Sign in to CampusFlow' }).click();
    await expect(
      browser.getByRole('heading', { name: 'A clearer view. A better day.' }),
    ).toBeVisible();
    await expect(browser.getByRole('link', { name: /Departments/ }).last()).toBeVisible();
    await noOverflow(browser);
    await browser.screenshot({ path: `.artifacts/screenshots/admin-${width}.png`, fullPage: true });
    await navigate(browser, 'Departments');
    await expect(
      browser.getByRole('cell', { name: 'Computer Science', exact: true }),
    ).toBeVisible();
    await browser.getByRole('button', { name: 'Add department' }).click();
    const dialog = browser.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByLabel('Department name').fill('Mathematics');
    await dialog.getByLabel('Department code').fill('MATH');
    await noOverflow(browser);
    await dialog.getByRole('button', { name: 'Create record' }).click();
    await expect(browser.getByRole('cell', { name: 'Mathematics', exact: true })).toBeVisible();
    for (const [label, heading] of [
      ['Faculty', 'Faculty'],
      ['Students', 'Students'],
      ['Academic batches', 'Academic batches'],
      ['Sections', 'Sections'],
      ['Accounts & access', 'Accounts'],
    ] as const) {
      await navigate(browser, label);
      await expect(browser.getByRole('heading', { name: heading, exact: true })).toBeVisible();
      await noOverflow(browser);
    }
    await navigate(browser, 'Students');
    await browser.getByRole('button', { name: 'Edit Test Student' }).click();
    await expect(browser.getByRole('dialog').getByLabel('Enrollment number')).toHaveAttribute(
      'readonly',
      '',
    );
    await browser.getByRole('button', { name: 'Close dialog' }).click();
    await browser.getByRole('button', { name: 'Switch to dark mode' }).click();
    await expect(browser.locator('html')).toHaveClass('dark');
    await noOverflow(browser);
    await browser.screenshot({ path: `.artifacts/screenshots/dark-${width}.png`, fullPage: true });
    const stored = await browser.evaluate(() => ({ ...localStorage }));
    expect(stored).toEqual({ 'campusflow-theme': 'dark' });
    const toggle = browser.getByRole('button', { name: 'Open navigation' });
    if (await toggle.isVisible()) await toggle.click();
    await browser.getByRole('button', { name: 'Sign out', exact: true }).first().click();
    await expect(browser).toHaveURL('/login');
  });
  for (const role of ['STUDENT', 'FACULTY'] as const) {
    test(`${role} ownership, directory and role boundary at ${width}px`, async ({
      page: browser,
    }) => {
      await browser.setViewportSize({ width, height: 900 });
      await mockApi(browser, role);
      await browser.goto('/login');
      await browser.getByLabel('Email address').fill('test@example.edu');
      await browser.getByLabel('Password', { exact: true }).fill('fixture-password');
      await browser.getByRole('button', { name: 'Sign in to CampusFlow' }).click();
      await expect(browser.getByRole('heading', { name: /Welcome, Test/ })).toBeVisible();
      await noOverflow(browser);
      await navigate(browser, 'My profile');
      await expect(browser.getByRole('heading', { name: 'My profile', exact: true })).toBeVisible();
      await noOverflow(browser);
      await navigate(browser, 'Faculty directory');
      await expect(browser.getByRole('cell', { name: 'Test Faculty', exact: true })).toBeVisible();
      await expect(browser.getByRole('columnheader', { name: 'Employee code' })).toHaveCount(0);
      await noOverflow(browser);
      await browser.evaluate(() => {
        history.pushState({}, '', '/admin/students');
        dispatchEvent(new PopStateEvent('popstate'));
      });
      await expect(
        browser.getByRole('heading', { name: 'This space belongs to another role.' }),
      ).toBeVisible();
      await browser.reload();
      await browser.goto('/app');
      await expect(browser).toHaveURL('/login');
    });
  }
}

test('mobile keyboard focus, Escape and modal return focus', async ({ page: browser }) => {
  await browser.setViewportSize({ width: 375, height: 900 });
  await mockApi(browser, 'ADMIN');
  await browser.goto('/login');
  await browser.getByLabel('Email address').fill('test@example.edu');
  await browser.getByLabel('Password', { exact: true }).fill('fixture-password');
  await browser.getByRole('button', { name: 'Sign in to CampusFlow' }).click();
  await expect(
    browser.getByRole('heading', { name: 'A clearer view. A better day.' }),
  ).toBeVisible();
  const toggle = browser.getByRole('button', { name: 'Open navigation' });
  await toggle.click();
  await expect(browser.locator('.main-shell')).toHaveAttribute('inert', '');
  await browser.keyboard.press('Tab');
  expect(
    await browser.evaluate(() => document.activeElement?.closest('#navigation') !== null),
  ).toBe(true);
  await browser.keyboard.press('Escape');
  await expect(toggle).toBeFocused();
  await expect(browser.locator('.main-shell')).not.toHaveAttribute('inert', '');
  await navigate(browser, 'Departments');
  const add = browser.getByRole('button', { name: 'Add department' });
  await add.click();
  await expect(browser.getByRole('dialog')).toBeVisible();
  await browser.keyboard.press('Tab');
  expect(await browser.evaluate(() => document.activeElement?.closest('dialog') !== null)).toBe(
    true,
  );
  await browser.keyboard.press('Escape');
  await expect(browser.getByRole('dialog')).toHaveCount(0);
  await expect(add).toBeFocused();
  await noOverflow(browser);
});
