import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const flow = new Trend('login_to_submit_seconds', true);
const resultTime = new Trend('result_compute_ms', true);
const users = JSON.parse(open(__ENV.USERS_FILE || './users.json'));
const examId = __ENV.EXAM_ID;
const baseUrl = (__ENV.BASE_URL || 'http://localhost:3000').replace(/\/$/, '');

export const options = {
  scenarios: {
    exam_flow: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 500),
      iterations: 1,
      maxDuration: '10m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
    login_to_submit_seconds: ['p(95)<30000'],
    result_compute_ms: ['p(95)<5000'],
  },
};

export default function () {
  const user = users[__VU - 1];
  if (!user || !examId) fail('Provide one unused assigned student account per VU and EXAM_ID.');
  const started = Date.now();
  const login = http.post(`${baseUrl}/api/auth/login`, JSON.stringify(user), { headers: { 'Content-Type': 'application/json' } });
  check(login, { 'login succeeded': (r) => r.status === 200 });
  if (login.status !== 200) return;
  const token = login.json('accessToken');
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  const start = http.post(`${baseUrl}/api/exams/${examId}/attempts`, null, { headers });
  check(start, { 'attempt started': (r) => r.status === 200 });
  if (start.status !== 200) return;
  const attemptId = start.json('id');
  const attempt = http.get(`${baseUrl}/api/attempts/${attemptId}`, { headers });
  check(attempt, { 'attempt loaded without answer key': (r) => r.status === 200 && (r.json('questions') || []).every((q) => !Object.hasOwn(q, 'correct_answer') && !Object.hasOwn(q, 'correctAnswer')) });
  if (attempt.status !== 200) return;
  const questions = attempt.json('questions') || [];
  if (questions.length) {
    const question = questions[0];
    const options = question.options || [];
    const first = options.length ? (typeof options[0] === 'object' ? options[0].id ?? options[0].value ?? options[0].text : options[0]) : null;
    const save = http.put(`${baseUrl}/api/attempts/${attemptId}/answers/${question.id}`, JSON.stringify({ answer: first, markedForReview: false }), { headers });
    check(save, { 'answer saved': (r) => r.status === 200 });
  }
  const gradingStarted = Date.now();
  const submit = http.post(`${baseUrl}/api/attempts/${attemptId}/submit`, null, { headers });
  resultTime.add(Date.now() - gradingStarted);
  check(submit, { 'result computed and stored': (r) => r.status === 200 && r.json('score') !== undefined });
  flow.add((Date.now() - started) / 1000);
}
