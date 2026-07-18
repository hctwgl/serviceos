import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 45_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'target/playwright-report' }]],
  use: {
    baseURL: 'http://127.0.0.1:5174',
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: false,
    env: {
      VITE_SERVICEOS_API_BASE_URL: '/api/v1',
      VITE_SERVICEOS_CLIENT_VERSION: '0.1.0-e2e.1',
      VITE_DEV_OIDC_ENABLED: 'true',
      VITE_DEV_OIDC_ISSUER: 'http://identity.serviceos.test/realms/serviceos',
      VITE_DEV_OIDC_CLIENT_ID: 'serviceos-network-web-e2e',
    },
  },
})
