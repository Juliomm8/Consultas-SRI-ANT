import { defineConfig } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL
if (!baseURL) {
  throw new Error('Run the E2E suite with `npm run test:e2e` so local services are isolated.')
}

export default defineConfig({
  testDir: '.',
  testMatch: '**/consultations.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  outputDir: '../node_modules/.cache/e2e-results',
  use: {
    baseURL,
    browserName: 'chromium',
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
