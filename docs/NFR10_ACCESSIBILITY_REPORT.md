# NFR-10 Accessibility Verification Record

## Inspected and implemented

The React portal provides a skip link, semantic main/navigation/heading/section landmarks, associated visible labels for form controls, `role="alert"` for errors, `role="status"` for success notices, focus-visible outlines, and focus movement to the authenticated content and active exam heading. Narrow layouts wrap the portal navigation; exam tables have an overflow container.

Automated axe checks are added for the sign-in screen and student dashboard using WCAG 2.1 A/AA rule tags. They do not cover every teacher/admin/attempt/modal state, and automated checks cannot establish full WCAG conformance.

## Verification results

- Automated test command: `cd frontend; npm ci; npm run test:browser`.
- Automated result (2026-11-20): **2 axe scans completed** (sign-in and Student dashboard), with **0 serious or critical violations**. Lower-impact findings were not used as a WCAG pass/fail gate in this check; examine the saved Playwright output before making further claims.
- Manual keyboard-only review: **not performed in this verification**.
- Manual contrast measurement with approved tooling: **not performed in this verification**.
- Screen-reader/browser testing: **not performed**.
- WCAG 2.1 AA conformance: **not claimed**.

Follow-up: test all role-specific pages, dialogs, question attempt and validation/error states with keyboard and screen readers; measure all text/control contrast combinations; fix and retest every reported issue.
