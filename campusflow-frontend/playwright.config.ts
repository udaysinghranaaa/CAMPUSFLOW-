import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  timeout: 45000,
  outputDir: 'test-results',
  use: {
    baseURL: 'http://localhost:5173',
    channel: 'chrome',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: {
    command: 'npm.cmd run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: false,
    timeout: 60000,
  },
});
