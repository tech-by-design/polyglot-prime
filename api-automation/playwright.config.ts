import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : 1,
  timeout: 60000, // 60 seconds for each test
  reporter: 'html',
  use: {
    actionTimeout: 0, // No timeout by default
    headless: true, // Run tests in headless mode
    trace: 'on-first-retry',
  },

});
