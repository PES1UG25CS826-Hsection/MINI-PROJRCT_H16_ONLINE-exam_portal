# NFR-06 Browser Compatibility Record

## Automated coverage added

The frontend now has Playwright browser checks for the registration-to-login flow, student dashboard rendering at a 360 CSS-pixel viewport, horizontal page overflow, and automated axe checks on sign-in and the student dashboard. Tests use stubbed API responses; they do not demonstrate a live backend/browser exam workflow.

Run from `frontend/` with `npm ci` and `npm run test:browser`. The Playwright project uses the installed Microsoft Edge channel. Install the browsers required by the project before running it in a new environment.

## Browser matrix

| Browser | Version | Result | Evidence |
|---|---|---|---|
| Microsoft Edge | 157.0.4341.60 | Passed: 4 Playwright tests | Local Edge run on 2026-11-20; mocked API responses |
| Google Chrome | Not installed | Not tested | No compatible binary found in standard installation path |
| Mozilla Firefox | Not installed | Not tested | No compatible binary found in standard installation path |
| Safari | Not available on this Windows host | Not tested | No Safari runtime available |

Desktop viewport: 1280×800 configured. Mobile viewport: 360×780 configured. A passing responsive test means the tested page has no document-level horizontal overflow at that viewport; it is not a complete device/browser compatibility certification.

## Run record

Date: 2026-11-20. Automated result: **4 passed, 0 failed** (registration/sign-in, student 360px overflow, teacher/admin mobile navigation, and axe scan on sign-in/student dashboard). API responses were stubbed; no live backend workflow was exercised in a browser. Playwright's Vite webServer integration did not work reliably on this Windows host, so Vite was started separately and Playwright was run against it. No manual cross-browser testing is claimed.
