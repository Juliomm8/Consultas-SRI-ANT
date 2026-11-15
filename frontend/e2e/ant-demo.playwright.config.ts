import { defineConfig } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL
if (!baseURL) {
  throw new Error('Run this suite with `npm run test:e2e:ant` so its backend and proxy are isolated.')
}

export default defineConfig({
  testDir: '.',
  testMatch: '**/ant-demo.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  outputDir: '../node_modules/.cache/ant-demo-e2e-results',
  use: {
    baseURL,
    browserName: 'chromium',
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
