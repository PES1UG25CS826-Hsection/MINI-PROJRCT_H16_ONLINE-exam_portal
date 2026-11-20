import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const studentSession = {
  token: 'browser-test-token',
  user: { id: 'student-1', email: 'student@example.test', fullName: 'Example Student', role: 'STUDENT' },
};

async function mockStudentApi(page) {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/auth/register') {
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ message: 'Registration successful.' }) });
    } else if (url.pathname === '/api/auth/login') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(studentSession) });
    } else if (url.pathname === '/api/exams' || url.pathname === '/api/results') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    } else {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({}) });
    }
  });
}

test('registration and sign-in flow works with accessible form controls', async ({ page }) => {
  await mockStudentApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Full name').fill('Example Student');
  await page.getByLabel('Email', { exact: true }).fill('student@example.test');
  await page.getByLabel('Password', { exact: true }).fill('StrongPass1!');
  await page.getByLabel('Confirm password').fill('StrongPass1!');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByRole('status')).toContainText('Registration successful');
  await page.getByLabel('Email', { exact: true }).fill('student@example.test');
  await page.getByLabel('Password', { exact: true }).fill('StrongPass1!');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Your exams' })).toBeVisible();
});

test('student dashboard fits a 360px mobile viewport without horizontal page overflow', async ({ page }) => {
  await mockStudentApi(page);
  await page.setViewportSize({ width: 360, height: 780 });
  await page.addInitScript((session) => localStorage.setItem('online-exam-session', JSON.stringify(session)), studentSession);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Your exams' })).toBeVisible();
  const dimensions = await page.evaluate(() => ({ body: document.body.scrollWidth, viewport: window.innerWidth }));
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport);
});

test('teacher and admin navigation fit a 360px mobile viewport', async ({ page }) => {
  await mockStudentApi(page);
  await page.setViewportSize({ width: 360, height: 780 });
  for (const role of ['TEACHER', 'ADMIN']) {
    await page.addInitScript((value) => localStorage.setItem('online-exam-session', JSON.stringify(value)), {
      token: 'browser-test-token',
      user: { ...studentSession.user, role },
    });
    await page.goto('/');
    await expect(page.getByRole('navigation', { name: 'Portal sections' })).toBeVisible();
    const dimensions = await page.evaluate(() => ({ body: document.body.scrollWidth, viewport: window.innerWidth }));
    expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport);
  }
});

test('sign-in and student dashboard have no serious or critical axe violations', async ({ page }) => {
  await mockStudentApi(page);
  await page.goto('/');
  const authResults = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(authResults.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact))).toEqual([]);
  await page.addInitScript((session) => localStorage.setItem('online-exam-session', JSON.stringify(session)), studentSession);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Your exams' })).toBeVisible();
  const dashboardResults = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(dashboardResults.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact))).toEqual([]);
});
