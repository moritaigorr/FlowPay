import { defineConfig } from '@playwright/test'

const baseURL = process.env.FLOWPAY_E2E_BASE_URL ?? 'http://127.0.0.1:5173'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  outputDir: './test-results',
  use: {
    baseURL,
    channel: process.env.CI ? undefined : 'chrome',
    headless: true,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
