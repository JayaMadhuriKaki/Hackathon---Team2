import { defineConfig, devices } from '@playwright/test'

// E2E tests live in ../tests/frontend/e2e (CLAUDE.md repo map).
//  - project "ui":    browser checks against the Vite dev server with the API mocked (no backend needed)
//  - project "stack": the demo path against the seeded Docker Compose stack (docker compose up first)
const STACK_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000'
const UI_PORT = 5179

export default defineConfig({
  testDir: '../tests/frontend/e2e',
  outputDir: './test-results', // git-ignored
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {}),
  },
  projects: [
    {
      name: 'ui',
      testMatch: /ui-.*\.spec\.js/,
      use: { ...devices['Desktop Chrome'], baseURL: `http://localhost:${UI_PORT}`, viewport: { width: 1280, height: 720 } },
    },
    {
      name: 'stack',
      testMatch: /demo-path\.spec\.js/,
      use: { ...devices['Desktop Chrome'], baseURL: STACK_URL, viewport: { width: 1280, height: 720 } },
    },
  ],
  webServer: process.env.E2E_SKIP_WEBSERVER
    ? undefined
    : {
        command: `npm run dev -- --port ${UI_PORT} --strictPort`,
        url: `http://localhost:${UI_PORT}`,
        reuseExistingServer: true,
        timeout: 60_000,
      },
})
