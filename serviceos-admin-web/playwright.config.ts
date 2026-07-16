import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 45_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'target/playwright-report' }]],
  use: {
    // 必须与 Keycloak realm 中登记的 redirect URI 主机名完全一致。
    baseURL: process.env.SERVICEOS_ADMIN_BASE_URL ?? 'http://localhost:5173',
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
