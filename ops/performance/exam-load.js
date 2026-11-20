import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const flow = new Trend('login_to_result_ms', true);
const resultTime = new Trend('result_compute_ms', true);
const users = JSON.parse(open(__ENV.USERS_FILE || './users.json'));
const examId = __ENV.EXAM_ID;
const baseUrl = (__ENV.BASE_URL || 'http://localhost:3000').replace(/\/$/, '');
const apiUrls = (__ENV.API_URLS || baseUrl).split(',').map((url) => url.trim().replace(/\/$/, '')).filter(Boolean);
const answer = __ENV.ANSWER_JSON === undefined ? undefined : JSON.parse(__ENV.ANSWER_JSON);
const expectedScore = __ENV.EXPECTED_SCORE === undefined ? undefined : Number(__ENV.EXPECTED_SCORE);
const expectedQuestionCount = __ENV.EXPECTED_QUESTION_COUNT === undefined ? undefined : Number(__ENV.EXPECTED_QUESTION_COUNT);
const vuCount = Number(__ENV.VUS || 500);
let requestNumber = 0;

function apiUrl() {
  const url = apiUrls[requestNumber % apiUrls.length];
  requestNumber += 1;
  return url;
}

export const options = {
  scenarios: {
    exam_flow: {
      executor: 'per-vu-iterations',
      vus: vuCount,
      iterations: 1,
      maxDuration: '10m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
    login_to_result_ms: ['p(95)<30000'],
    result_compute_ms: ['p(95)<5000'],
  },
};

export function setup() {
  if (!examId) throw new Error('Set EXAM_ID to a published and available exam.');
  if (!Number.isInteger(vuCount) || vuCount < 1 || users.length < vuCount) {
    throw new Error(`VUS is ${vuCount}, but USERS_FILE contains ${users.length} accounts.`);
  }
  if (!apiUrls.length) throw new Error('Set BASE_URL or provide one or more URLs in API_URLS.');
  if (expectedScore !== undefined && !Number.isFinite(expectedScore)) throw new Error('EXPECTED_SCORE must be numeric.');
  if (expectedQuestionCount !== undefined && (!Number.isInteger(expectedQuestionCount) || expectedQuestionCount < 1)) {
    throw new Error('EXPECTED_QUESTION_COUNT must be a positive integer.');
  }
}

export default function () {
  const user = users[__VU - 1];
  if (!user) fail('Provide one unused assigned student account per VU.');
  const started = Date.now();
  const login = http.post(`${apiUrl()}/api/auth/login`, JSON.stringify(user), { headers: { 'Content-Type': 'application/json' } });
  check(login, { 'login succeeded': (r) => r.status === 200 });
  if (login.status !== 200) return;
  const token = login.json('accessToken');
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  const start = http.post(`${apiUrl()}/api/exams/${examId}/attempts`, null, { headers });
  check(start, { 'attempt started': (r) => r.status === 200 });
  if (start.status !== 200) return;
  const attemptId = start.json('id');
  const attempt = http.get(`${apiUrl()}/api/attempts/${attemptId}`, { headers });
  check(attempt, { 'attempt loaded without answer key': (r) => r.status === 200 && (r.json('questions') || []).every((q) => !Object.hasOwn(q, 'correct_answer') && !Object.hasOwn(q, 'correctAnswer')) });
  if (attempt.status !== 200) return;
  const questions = attempt.json('questions') || [];
  check(questions, { 'exam has expected question count': (items) => expectedQuestionCount === undefined || items.length === expectedQuestionCount });
  if (answer !== undefined && questions.length) {
    const question = questions[0];
    const save = http.put(`${apiUrl()}/api/attempts/${attemptId}/answers/${question.id}`, JSON.stringify({ answer, markedForReview: false }), { headers });
    check(save, { 'answer saved': (r) => r.status === 200 });
  }
  const gradingStarted = Date.now();
  const submit = http.post(`${apiUrl()}/api/attempts/${attemptId}/submit`, null, { headers });
  resultTime.add(Date.now() - gradingStarted);
  check(submit, { 'result computed and stored': (r) => r.status === 200 && r.json('score') !== undefined });
  if (submit.status !== 200) return;
  const result = http.get(`${apiUrl()}/api/attempts/${attemptId}/result`, { headers });
  check(result, { 'persisted result is available': (r) => r.status === 200 && r.json('score') !== undefined });
  if (result.status === 200) {
    check(result, {
      'persisted score matches expected score': (r) => expectedScore === undefined || Number(r.json('score')) === expectedScore,
      'persisted score matches submit response': (r) => Number(r.json('score')) === Number(submit.json('score')),
    });
  }
  flow.add(Date.now() - started);
}
