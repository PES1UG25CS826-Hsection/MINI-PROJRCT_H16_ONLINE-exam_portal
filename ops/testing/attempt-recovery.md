# Attempt recovery evidence checklist

Use a disposable exam and student account in a deployment with the same database and API topology as the target environment. Record the deployment revision, exam/attempt IDs, student account identifier (never its password), browser/version, start time, and network interruption duration. Do not use a real student attempt.

1. Sign in, start an assigned exam, answer at least three questions, and wait until the UI reports **All answers saved**. Record the attempt ID and saved-answer state.
2. Use browser developer tools to set the network to **Offline**. Change one previously answered question and mark a second question for review. Record when the connection was interrupted.
3. Keep the interruption at five minutes or less. Restore **Online**, wait for the UI to report **All answers saved**, then reload the page.
4. Sign in again if needed. Confirm the active attempt resumes with the server's remaining time, the three previously saved answers, the newly edited answer, and review marks. Confirm the server time continues to run while offline; the browser must not add the disconnected duration back to the attempt.
5. Repeat with a fresh attempt and reload the page while the network remains online. Confirm all server-saved answers and review marks are restored.

| Run | Revision | Attempt | Outage duration | Saved answers before | Restored answers after reconnect/reload | Remaining time restored | Pass / fail | Evidence link |
|---|---|---|---:|---:|---:|---|---|---|
| | | | | | | | | |

Attach browser screenshots or a short recording and relevant sanitized API responses. Never include access tokens, passwords, or answer keys in the evidence. The checklist is a procedure, not a measured result; record an actual run before closing the recovery issue.
