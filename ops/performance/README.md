# Load and result timing evidence

`exam-load.js` runs a login-to-submit journey with one independent assigned student per virtual user. It checks that the exam API omits answer keys before submission, optionally saves a known answer, submits, and reads the persisted result back. `API_URLS` can list direct API instance URLs; the script rotates each step across them so an attempt started on one instance is read, saved, submitted, and checked on other instances.

## Prepare a representative run

1. Deploy the same commit and settings you intend to measure. Record the API image/revision, database size and connection limits, CPU/memory limits, region, and network path.
2. Create a published, currently available exam and assign distinct disposable student accounts. For the scoring and cross-instance runs, include a first one-point question whose known correct answer is `A`; the other questions can remain unanswered. For issue #44, use exactly 100 objective questions. Use the same production configuration for negative marking and result review.
3. Create a local `users.json` containing one unused account per virtual user:

   ```json
   [{"email":"load-student-001@example.invalid","password":"a-local-test-password"}]
   ```

   Add enough accounts for the selected VU count (500 for #43 or 1,000 for #46). Keep this file out of Git; it contains credentials. Use a disposable test deployment and reset/recreate accounts and attempts between runs. Accounts must be assigned to the selected exam.

4. Run the test from a host with k6 installed. For #43, the 500-VU journey checks every API response in the login/start/load/save/submit/result sequence against the 2-second p95 target:

   ```sh
   k6 run -e BASE_URL=https://exam-test.example.edu -e EXAM_ID=<available-exam-uuid> -e USERS_FILE=./users.json -e VUS=500 -e ANSWER_JSON='"A"' -e EXPECTED_SCORE=1 --summary-export=issue-43-summary.json ops/performance/exam-load.js
   ```

   For #44, use at least 20 distinct accounts against the same 100-question exam. Configure `ANSWER_JSON` and `EXPECTED_SCORE` from the known test fixture; `EXPECTED_QUESTION_COUNT=100` checks the exam size. The script checks the returned score against the expected score and fetches it again from the result endpoint. The result-computation p95 must be below 5 seconds.

   ```sh
   k6 run -e BASE_URL=https://exam-test.example.edu -e EXAM_ID=<available-exam-uuid> -e USERS_FILE=./users.json -e VUS=20 -e EXPECTED_QUESTION_COUNT=100 -e ANSWER_JSON='"A"' -e EXPECTED_SCORE=1 --summary-export=issue-44-summary.json ops/performance/exam-load.js
   ```

   For #46, provide direct URLs for every API replica that are reachable from the k6 host, disable session affinity for this run, and let successive requests for each attempt hit different instances:

   ```sh
   k6 run -e API_URLS=https://api-1.exam-test.example.edu,https://api-2.exam-test.example.edu,https://api-3.exam-test.example.edu -e EXAM_ID=<available-exam-uuid> -e USERS_FILE=./users-1000.json -e VUS=1000 -e ANSWER_JSON='"A"' -e EXPECTED_SCORE=1 --summary-export=issue-46-summary.json ops/performance/exam-load.js
   ```

5. Save the complete k6 summaries, test settings, deployment revision, instance list, database size/connection limits, CPU/memory limits, region, and network path with the sprint evidence. Checks fail if any request/result assertion fails. The thresholds require request p95 below 2 seconds, result submission p95 below 5 seconds, and failed requests under 1%.

Do not report a pass unless the measured run completed with all thresholds met. The repository does not include credentials, an exam instance, or measured results. The scripts configure reproducible tests; they are not evidence that a deployment met the targets.
