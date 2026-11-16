import { defineConfig } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL
if (!baseURL) {
  throw new Error('Run this suite with `npm run test:e2e:ant:redis` to start isolated local services.')
}

export default defineConfig({
  testDir: '.',
  testMatch: '**/ant-demo.redis.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: process.env.E2E_PLAYWRIGHT_JSON_REPORT_PATH
    ? [['list'], ['json', { outputFile: process.env.E2E_PLAYWRIGHT_JSON_REPORT_PATH }]]
    : 'list',
  outputDir: '../node_modules/.cache/ant-demo-redis-e2e-results',
  use: {
    baseURL,
    browserName: 'chromium',
    headless: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
