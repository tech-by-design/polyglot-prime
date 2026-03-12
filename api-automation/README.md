# TechBD API Testing

## Overview

This repository provides an automated Bundle negative testing for TechBD. The
testing framework is built using Node.js, Playwright, and TypeScript, ensuring
efficient and reliable testing based on predefined criteria.

## Prerequisites

Before using the automation solution, ensure the following prerequisites are
met:

- **Node.js and npm:** Install Node.js and npm on your machine. You can download
  them from [https://nodejs.org/](https://nodejs.org/).
-
- -**Visual Studio Code (VS Code):** It is recommended to use Visual Studio Code
  for writing and managing Playwright test scripts, as it offers a rich
  development environment with extensions for JavaScript, TypeScript, and
  Playwright.

## Installation

1. **Navigate to Repository:**

   ```bash
   cd api-automation
   ```

2. **Install Dependencies:**
   ```bash
   npm install
   ```

## Running Tests

1. **Run Individual Test:** Navigate to the root folder and execute the
   following command to run a specific test script::

   ```cmd
   npx playwright test <test_script_name>.test.ts
   ```

2. **Parallel Execution:** For parallel execution of tests, use the following
   command:

   ```cmd
   npx playwright test
   ```

## Additional Notes

- **Test Results:** After execution of tests results will be available in
  `playwright-report` folder,To view the report, run the following command:

  ```cmd
  npx playwright show-report
  ```

- **Dependency Installation:** Ensure all required dependencies are installed
  before running commands.
