import { expect, test } from '@playwright/test';
import { me, tokens, page as paged, department } from '../src/test/fixtures';
for (const width of [375, 1280]) {
  test(`Phase5 admin timetable, publish conflict and finder at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const term = {
      id: 1,
      name: 'Autumn',
      departmentId: 1,
      semester: 1,
      startDate: '2026-10-19',
      endDate: '2026-11-02',
      active: true,
    };
    const session = {
      id: 1,
      academicTermId: 1,
      sectionId: 1,
      sectionName: 'A',
      subjectId: 1,
      subjectName: 'Algorithms',
      facultyId: 1,
      facultyName: 'Test Faculty',
      roomId: 1,
      roomCode: 'R1',
      dayOfWeek: 1,
      startTime: '09:00:00',
      endTime: '10:00:00',
      effectiveStartDate: term.startDate,
      effectiveEndDate: term.endDate,
      status: 'DRAFT',
      sessionType: 'LECTURE',
    };
    const found = {
      id: 1,
      fullName: 'Test Faculty',
      departmentCode: 'CSE',
      designation: 'Lecturer',
      subjects: ['Algorithms'],
      state: 'UNKNOWN',
      explanation: 'Schedule coverage is incomplete.',
      date: '2026-10-19',
      startTime: '09:00',
      endTime: '10:00',
    };
    await page.route('http://localhost:8080/api/**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      const method = route.request().method();
      const headers = {
        'access-control-allow-origin': new URL(page.url()).origin,
        'access-control-allow-headers': 'Authorization,Content-Type',
        'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
      };
      const send = (body: unknown, status = 200) =>
        route.fulfill({
          status,
          headers,
          contentType: 'application/json',
          body: JSON.stringify(body),
        });
      if (method === 'OPTIONS') return route.fulfill({ status: 204, headers });
      if (path.endsWith('/login')) return send(tokens());
      if (path.endsWith('/auth/me')) return send(me());
      if (path === '/api/v1/timetable/terms') return send(paged([term]));
      if (path === '/api/v1/timetable/sessions') return send(paged([session]));
      if (path === '/api/v1/timetable/admin/week')
        return send({
          weekStart: '2026-10-19',
          timezone: 'Asia/Kolkata',
          sessions: [{ ...session, sessionId: 1, date: '2026-10-19' }],
        });
      if (path === '/api/v1/timetable/sessions/1/publish')
        return send(
          {
            status: 409,
            message: 'Faculty scheduling conflict. Choose a different slot.',
            fieldErrors: {},
          },
          409,
        );
      if (path === '/api/v1/faculty-finder') return send(paged([found]));
      if (path.endsWith('/availability')) return send(found);
      if (path === '/api/v1/departments') return send(paged([department]));
      return send(paged([]));
    });
    await page.goto('/login');
    await page.getByLabel('Email address').fill('test@example.edu');
    await page.getByLabel('Password', { exact: true }).fill('fixture-password');
    await page.getByRole('button', { name: 'Sign in to CampusFlow' }).click();
    await expect(
      page.getByRole('heading', { name: 'A clearer view. A better day.' }),
    ).toBeVisible();
    const navigate = async (label: string) => {
      const toggle = page.getByRole('button', { name: 'Open navigation' });
      if (await toggle.isVisible()) await toggle.click();
      await page
        .getByRole('navigation', { name: 'Main navigation' })
        .getByRole('link', { name: label, exact: true })
        .click();
    };
    await navigate('Timetable builder');
    await page.getByLabel('Academic term').selectOption('1');
    await expect(page.getByRole('button', { name: 'Manage Algorithms' })).toBeVisible();
    await page.getByRole('button', { name: 'Manage Algorithms' }).click();
    await page.getByRole('button', { name: 'Publish', exact: true }).click();
    await page.getByRole('button', { name: 'Confirm publish' }).click();
    await expect(page.getByRole('alert')).toContainText('Faculty scheduling conflict.');
    await page.getByRole('button', { name: 'Close dialog' }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await navigate('Faculty Finder');
    await expect(page.getByRole('heading', { name: 'Test Faculty', exact: true })).toBeVisible();
    await expect(page.getByText('Unknown', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'View Test Faculty' }).click();
    await expect(page.getByRole('dialog')).toContainText('Schedule coverage is incomplete.');
    await page.getByRole('button', { name: 'Close dialog' }).click();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({
      path: `.artifacts/screenshots/phase5-finder-${width}.png`,
      fullPage: true,
    });
  });
}
