import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.FLOWPAY_BASE_URL ?? 'http://127.0.0.1:8080'

export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    ...devices['Desktop Chrome'],
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: process.env.FLOWPAY_RECORD_VIDEO === '1' ? 'on' : 'retain-on-failure',
    ignoreHTTPSErrors: false,
  },
})
