# Load and result timing evidence

`exam-load.js` runs a login-to-submit journey with one independent assigned student per virtual user. It checks that the exam API omits answer keys before submission, saves one answer, submits, and records total flow and result-computation latency.

## Prepare a representative run

1. Deploy the same commit and settings you intend to measure. Record the API image/revision, database size and connection limits, CPU/memory limits, region, and network path.
2. Create an exam that is published and currently available, assign at least 500 distinct student accounts, and include 100 objective questions for the result-computation measurement. Use the same production configuration for negative marking and result review.
3. Create a local `users.json` containing one unused account per virtual user:

   ```json
   [{"email":"load-student-001@example.invalid","password":"a-local-test-password"}]
   ```

   Add 499 more test accounts. Keep this file out of Git; it contains credentials. Use a disposable test deployment and reset/recreate accounts and attempts between runs.

4. Run the test from a host with k6 installed:

   ```sh
   k6 run -e BASE_URL=https://exam-test.example.edu -e EXAM_ID=<available-exam-uuid> -e USERS_FILE=./users.json -e VUS=500 ops/performance/exam-load.js
   ```

5. Save the complete k6 summary, test settings, deployment revision, and any environment limits with the sprint evidence. The script thresholds fail unless request p95 is below 2 seconds, result submission p95 is below 5 seconds, and failed requests are under 1%.

Do not report a pass unless the measured run completed with all thresholds met. The repository does not include credentials, an exam instance, or measured results.
