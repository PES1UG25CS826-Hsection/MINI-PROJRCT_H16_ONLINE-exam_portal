# NFR-06 Browser Compatibility Record

## Automated coverage added

The frontend now has Playwright browser checks for the registration-to-login flow, student dashboard rendering at a 360 CSS-pixel viewport, horizontal page overflow, and automated axe checks on sign-in and the student dashboard. Tests use stubbed API responses; they do not demonstrate a live backend/browser exam workflow.

Run from `frontend/` with `npm ci` and `npm run test:browser`. The suite runs Chromium, Firefox, WebKit, and the installed Microsoft Edge channel. Install the browsers required by the project before running it in a new environment.

## Browser matrix

| Browser | Version | Result | Evidence |
|---|---|---|---|
| Playwright Chromium (Chrome for Testing) | 159.0.8154.6 (Playwright chromium v1255) | Passed: 4 Playwright tests | Installed Chromium runtime; API responses stubbed |
| Microsoft Edge | 157.0.4341.60 | Passed: 4 Playwright tests | System Edge channel; API responses stubbed |
| Mozilla Firefox | 159.0 (Playwright firefox v1572) | Passed: 4 Playwright tests | Playwright-managed Firefox; API responses stubbed |
| Playwright WebKit | 27.2 (Playwright webkit v2388) | Passed: 4 Playwright tests | WebKit on Windows; this is **not Safari** |
| Google Chrome (system channel) | Not installed/tested | Not tested | Chromium test above is Playwright Chrome for Testing, not a separate installed stable Chrome channel |
| Safari | Not available on this Windows host | Not tested | No Safari runtime; WebKit results are not Safari results |

Desktop viewport: 1280×800 configured. Mobile viewport: 360×780 configured. A passing responsive test means the tested page has no document-level horizontal overflow at that viewport; it is not a complete device/browser compatibility certification.

## Run record

Date: 2026-11-20. Automated result: **16 passed, 0 failed** across Chromium, Firefox, WebKit, and Edge (registration/sign-in, Student 360px overflow, Teacher/Admin mobile navigation, and axe checks in each project). API responses were stubbed; no live backend workflow was exercised in a browser. Playwright's Vite webServer integration did not work reliably on this Windows host, so Vite was started separately and Playwright was run against it. No manual cross-browser testing is claimed.
