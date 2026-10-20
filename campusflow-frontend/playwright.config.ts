import { defineConfig } from '@playwright/test';
const port = Number(process.env.CAMPUSFLOW_E2E_PORT ?? 5173);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid E2E port');
const baseURL = 'http://localhost:' + port;
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  timeout: 45000,
  outputDir: 'test-results',
  use: {
    baseURL,
    channel: 'chrome',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: {
    command: `node node_modules/vite/bin/vite.js --host localhost --port ${port} --strictPort`,
    url: baseURL,
    reuseExistingServer: false,
    timeout: 60000,
  },
});
