import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

const SESSION_KEY = 'exam-portal-session';
const queueKey = (id) => `exam-portal-pending:${id}`;

function App() {
  const [session, setSession] = useState(() => {
    try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); } catch { return null; }
  });
  const [groups, setGroups] = useState({ upcoming: [], available: [], completed: [] });
  const [attempt, setAttempt] = useState(null);
  const [result, setResult] = useState(null);
  const [activeQuestion, setActiveQuestion] = useState(0);
  const [remaining, setRemaining] = useState(0);
  const [authMode, setAuthMode] = useState('login');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [saveState, setSaveState] = useState('');
  const lastAutosave = useRef(Date.now());
  const flushing = useRef(false);
  const submitting = useRef(false);

  const api = useCallback(async (path, options = {}) => {
    const response = await fetch(`/api${path}`, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...(session?.accessToken ? { Authorization: `Bearer ${session.accessToken}` } : {}), ...options.headers },
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.detail || data.message || 'The request could not be completed.');
    return data;
  }, [session]);

  const loadExams = useCallback(async () => {
    const data = await api('/student/exams');
    setGroups(data.groups);
    const active = [...data.groups.upcoming, ...data.groups.available].find((exam) => exam.active_attempt);
    if (active?.active_attempt) await openAttempt(active.active_attempt);
  }, [api]);

  const openAttempt = useCallback(async (id) => {
    const data = await api(`/attempts/${id}`);
    if (data.status === 'IN_PROGRESS') {
      const pending = JSON.parse(localStorage.getItem(queueKey(id)) || '{}');
      const questions = data.questions.map((question) => ({ ...question, answer: Object.hasOwn(pending, question.id) ? pending[question.id].answer : question.answer, marked_for_review: Object.hasOwn(pending, question.id) ? pending[question.id].markedForReview : question.marked_for_review }));
      setAttempt({ ...data, questions }); setRemaining(data.remainingSeconds); setResult(null);
      const firstUnanswered = questions.findIndex((question) => question.answer === null || question.answer === undefined);
      setActiveQuestion(Math.max(0, firstUnanswered));
      await flushPending(id);
    } else {
      setAttempt(null); setResult(data.result || await api(`/attempts/${id}/result`));
    }
    setError('');
  }, [api]);

  const flushPending = useCallback(async (id) => {
    if (!session?.accessToken || !navigator.onLine || flushing.current) return;
    flushing.current = true;
    setSaveState('Saving answers…');
    try {
      while (navigator.onLine) {
        const pending = JSON.parse(localStorage.getItem(queueKey(id)) || '{}');
        const [questionId, item] = Object.entries(pending)[0] || [];
        if (!questionId) break;
        await api(`/attempts/${id}/answers/${questionId}`, { method: 'PUT', body: JSON.stringify(item) });
        const latest = JSON.parse(localStorage.getItem(queueKey(id)) || '{}');
        if (latest[questionId]?.updatedAt === item.updatedAt) delete latest[questionId];
        localStorage.setItem(queueKey(id), JSON.stringify(latest));
      }
      lastAutosave.current = Date.now();
      const remaining = JSON.parse(localStorage.getItem(queueKey(id)) || '{}');
      setSaveState(Object.keys(remaining).length ? 'Offline — answers are saved on this device and will sync when you reconnect.' : 'All answers saved');
    } catch (err) {
      setSaveState(navigator.onLine ? err.message : 'Offline — answers are saved on this device and will sync when you reconnect.');
    } finally {
      flushing.current = false;
    }
  }, [api, session]);

  useEffect(() => { if (session) loadExams().catch((err) => setError(err.message)); }, [session, loadExams]);
  useEffect(() => {
    const online = () => { if (attempt) flushPending(attempt.id); };
    window.addEventListener('online', online);
    return () => window.removeEventListener('online', online);
  }, [attempt, flushPending]);

  useEffect(() => {
    if (!attempt || attempt.status !== 'IN_PROGRESS') return undefined;
    const timer = window.setInterval(() => setRemaining((seconds) => Math.max(0, seconds - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [attempt]);

  useEffect(() => {
    if (!attempt || attempt.status !== 'IN_PROGRESS') return undefined;
    const poll = window.setInterval(() => {
      if (Date.now() - lastAutosave.current >= 25000) flushPending(attempt.id);
      if (remaining <= 1) submitAttempt(true).catch((err) => setError(err.message));
    }, 1000);
    return () => window.clearInterval(poll);
  }, [attempt, remaining, flushPending]);

  async function authenticate(event) {
    event.preventDefault(); setBusy(true); setError('');
    const form = new FormData(event.currentTarget);
    const body = Object.fromEntries(form.entries());
    try {
      const data = await api(`/auth/${authMode === 'register' ? 'register' : 'login'}`, { method: 'POST', body: JSON.stringify(body) });
      localStorage.setItem(SESSION_KEY, JSON.stringify(data)); setSession(data); setNotice(`Welcome, ${data.user.name}.`);
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  function signOut() {
    localStorage.removeItem(SESSION_KEY); setSession(null); setAttempt(null); setResult(null); setGroups({ upcoming: [], available: [], completed: [] });
  }

  async function beginExam(exam) {
    setBusy(true); setError('');
    try { const data = await api(`/exams/${exam.id}/attempts`, { method: 'POST' }); await openAttempt(data.id); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  function updateAnswer(question, answer, markedForReview = question.marked_for_review) {
    const id = attempt.id;
    const item = { answer, markedForReview, updatedAt: Date.now() };
    const pending = JSON.parse(localStorage.getItem(queueKey(id)) || '{}'); pending[question.id] = item;
    localStorage.setItem(queueKey(id), JSON.stringify(pending));
    setAttempt((current) => ({ ...current, questions: current.questions.map((q) => q.id === question.id ? { ...q, answer, marked_for_review: markedForReview } : q) }));
    setSaveState(navigator.onLine ? 'Answer saved on this device; syncing…' : 'Offline — answer saved on this device.');
    if (navigator.onLine) window.setTimeout(() => flushPending(id), 250);
  }

  async function submitAttempt(automatic = false) {
    if (!attempt) return;
    if (!automatic && !window.confirm('Submit this exam now? You will not be able to change your answers after submission.')) return;
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true); setError('');
    try {
      await flushPending(attempt.id);
      const pending = JSON.parse(localStorage.getItem(queueKey(attempt.id)) || '{}');
      if (Object.keys(pending).length) throw new Error('Some answers have not reached the server yet. Reconnect and try submitting again.');
      const submitted = await api(`/attempts/${attempt.id}/submit`, { method: 'POST' });
      setResult(submitted); setAttempt(null); setNotice(automatic ? 'Your time ended and the exam was submitted.' : 'Your exam has been submitted.');
      localStorage.removeItem(queueKey(attempt.id)); await loadExams();
    } catch (err) { setError(err.message); }
    finally { submitting.current = false; setBusy(false); }
  }

  const unanswered = useMemo(() => attempt?.questions.filter((q) => q.answer === null || q.answer === undefined).length || 0, [attempt]);
  const formatTime = (seconds) => `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

  if (!session) return <main className="auth-shell">
    <a className="brand" href="#home">EXAM<span>PORTAL</span></a>
    <div className="auth-card">
      <p className="eyebrow">ONLINE EXAMINATION</p>
      <h1>{authMode === 'login' ? 'Welcome back.' : 'Create your student account.'}</h1>
      <p className="muted">{authMode === 'login' ? 'Sign in to see your assessments and results.' : 'Your exams and progress, all in one place.'}</p>
      <form onSubmit={authenticate} className="auth-form">
        {authMode === 'register' && <label>Full name<input name="fullName" autoComplete="name" required maxLength="160" /></label>}
        <label>Email address<input name="email" type="email" autoComplete="email" required /></label>
        <label>Password<input name="password" type="password" autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} minLength="10" required /></label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={busy}>{busy ? 'Please wait…' : authMode === 'login' ? 'Sign in' : 'Create account'} <span aria-hidden="true">→</span></button>
      </form>
      <p className="switch-auth">{authMode === 'login' ? 'New to the portal?' : 'Already have an account?'} <button className="text-button" onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setError(''); }}>{authMode === 'login' ? 'Create an account' : 'Sign in'}</button></p>
    </div>
    <footer><span>Online Exam Portal</span><span>Built for focused learning</span></footer>
  </main>;

  return <main className="app-shell">
    <nav className="topbar"><a className="brand" href="#home">EXAM<span>PORTAL</span></a><div className="user-tools"><span>{session.user.name}</span><button className="quiet-button" onClick={signOut}>Sign out</button></div></nav>
    {error && <div className="banner error-banner" role="alert">{error}<button onClick={() => setError('')} aria-label="Dismiss">×</button></div>}
    {notice && <div className="banner success-banner" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss">×</button></div>}
    {attempt ? <section className="exam-view" aria-label="Exam attempt">
      <header className="exam-header"><div><p className="eyebrow">EXAM IN PROGRESS</p><h1>Assessment</h1><p className="muted">Your answers save as you work. You can return to any question.</p></div><div className={`timer ${remaining < 300 ? 'urgent' : ''}`} aria-live="polite"><span>TIME REMAINING</span><strong>{formatTime(remaining)}</strong></div></header>
      <div className="exam-layout">
        <aside className="question-nav"><h2>Questions</h2><p className="muted small">{attempt.questions.length - unanswered} of {attempt.questions.length} answered</p><div className="question-grid">{attempt.questions.map((q, i) => <button key={q.id} aria-label={`Question ${i + 1}${q.answer != null ? ', answered' : ', unanswered'}${q.marked_for_review ? ', marked for review' : ''}`} aria-current={activeQuestion === i ? 'step' : undefined} className={`question-chip ${activeQuestion === i ? 'current' : ''} ${q.answer != null ? 'answered' : ''} ${q.marked_for_review ? 'review' : ''}`} onClick={() => setActiveQuestion(i)}>{i + 1}</button>)}</div><div className="legend"><span><i className="dot done" /> Answered</span><span><i className="dot flag" /> Review</span><span><i className="dot" /> Unanswered</span></div></aside>
        <article className="question-card">
          {attempt.questions[activeQuestion] ? <>
            <div className="question-meta"><span>QUESTION {activeQuestion + 1} OF {attempt.questions.length}</span><span>{attempt.questions[activeQuestion].points} {Number(attempt.questions[activeQuestion].points) === 1 ? 'point' : 'points'}</span></div>
            <h2>{attempt.questions[activeQuestion].prompt}</h2>
            <div className="answer-options">{(attempt.questions[activeQuestion].options || []).map((option, i) => {
              const value = typeof option === 'object' ? option.id ?? option.value ?? option.text : option;
              const label = typeof option === 'object' ? option.text ?? option.label ?? option.value : option;
              return <label key={String(value)} className={`option ${attempt.questions[activeQuestion].answer === value ? 'selected' : ''}`}><input type="radio" name={attempt.questions[activeQuestion].id} checked={attempt.questions[activeQuestion].answer === value} onChange={() => updateAnswer(attempt.questions[activeQuestion], value)} /><span className="option-letter">{String.fromCharCode(65 + i)}</span><span>{label}</span></label>;
            })}</div>
            {attempt.questions[activeQuestion].question_type === 'SHORT_ANSWER' && <label className="short-answer">Your answer<textarea rows="4" value={attempt.questions[activeQuestion].answer ?? ''} onChange={(event) => updateAnswer(attempt.questions[activeQuestion], event.target.value)} /></label>}
            <div className="question-actions"><button className={`review-button ${attempt.questions[activeQuestion].marked_for_review ? 'marked' : ''}`} onClick={() => updateAnswer(attempt.questions[activeQuestion], attempt.questions[activeQuestion].answer, !attempt.questions[activeQuestion].marked_for_review)}>{attempt.questions[activeQuestion].marked_for_review ? '★ Marked for review' : '☆ Mark for review'}</button><span className="save-status" aria-live="polite">{saveState}</span></div>
            <div className="pager"><button className="secondary" disabled={activeQuestion === 0} onClick={() => setActiveQuestion((n) => n - 1)}>← Previous</button><button className="primary" onClick={() => activeQuestion < attempt.questions.length - 1 ? setActiveQuestion((n) => n + 1) : submitAttempt(false)}>{activeQuestion < attempt.questions.length - 1 ? 'Next question →' : 'Review and submit'}</button></div>
          </> : <p>This exam has no questions.</p>}
        </article>
      </div>
      <div className="submit-row"><span>{unanswered ? `${unanswered} unanswered question${unanswered === 1 ? '' : 's'}` : 'You have answered every question'}</span><button className="danger-button" disabled={busy} onClick={() => submitAttempt(false)}>Submit exam</button></div>
    </section> : result ? <section className="result-card"><div className="result-icon">✓</div><p className="eyebrow">EXAM COMPLETE</p><h1>{result.passed ? 'Well done.' : 'Assessment submitted.'}</h1><p className="muted">Your result has been recorded.</p><div className="score-line"><strong>{result.score}<small> / {result.maxScore}</small></strong><span>{result.percentage}%</span></div><span className={`result-pill ${result.passed ? 'pass' : 'fail'}`}>{result.passed ? 'PASS' : 'NOT PASSED'}</span>{result.questions?.length > 0 && <section className="review-results" aria-label="Question review"><h2>Question review</h2>{result.questions.map((question,index) => <article key={question.question_id}><div className="review-result-heading"><strong>Question {index + 1}</strong><span className={question.is_correct ? 'answer-correct' : 'answer-incorrect'}>{question.is_correct ? 'Correct' : 'Review'}</span></div><p>{question.prompt}</p><dl><div><dt>Your answer</dt><dd>{formatAnswer(question.answer)}</dd></div><div><dt>Correct answer</dt><dd>{formatAnswer(question.correct_answer)}</dd></div></dl></article>)}</section>}<div className="result-footer"><button className="primary" onClick={() => { setResult(null); loadExams(); }}>Back to my exams</button></div></section> : <section className="dashboard">
      <div className="dashboard-heading"><div><p className="eyebrow">STUDENT DASHBOARD</p><h1>Your exams, at a glance.</h1><p className="muted">Pick up where you left off, or see what’s coming next.</p></div><button className="quiet-button" onClick={() => loadExams().catch((err) => setError(err.message))}>Refresh ↻</button></div>
      <ExamGroup title="Available now" items={groups.available} onStart={beginExam} busy={busy} actionLabel="Start exam" />
      <ExamGroup title="Upcoming" items={groups.upcoming} onStart={() => {}} busy={busy} actionLabel="Opens later" />
      <ExamGroup title="Completed" items={groups.completed} onStart={async (exam) => { try { setResult(await api(`/attempts/${exam.latest_attempt}/result`)); } catch (err) { setError(err.message); } }} busy={busy} actionLabel="View result" />
      {!groups.upcoming.length && !groups.available.length && !groups.completed.length && <div className="empty-state"><div aria-hidden="true">✦</div><h2>No exams assigned yet</h2><p>When your teacher assigns an assessment, it will appear here.</p></div>}
    </section>}
    <footer className="app-footer"><span>Online Exam Portal</span><span>Server timed · Answers autosaved</span></footer>
  </main>;
}

function formatAnswer(value) {
  if (value === null || value === undefined) return 'Not answered';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

function ExamGroup({ title, items, onStart, busy, actionLabel }) {
  if (!items.length) return null;
  return <section className="exam-group"><div className="group-heading"><h2>{title}</h2><span>{items.length}</span></div><div className="exam-list">{items.map((exam) => <article className="exam-row" key={exam.id}><div className="exam-symbol">✦</div><div className="exam-details"><h3>{exam.title}</h3><p>{exam.description || 'Online assessment'} · {exam.duration_minutes} minutes</p>{exam.starts_at && <p className="exam-date">{new Date(exam.starts_at).toLocaleString()}</p>}</div><span className={`status-tag ${exam.status === 'IN_PROGRESS' ? 'live' : ''}`}>{exam.status === 'IN_PROGRESS' ? 'In progress' : title === 'Upcoming' ? 'Upcoming' : title === 'Completed' ? 'Completed' : 'Available'}</span><button className="secondary" onClick={() => onStart(exam)} disabled={busy || title === 'Upcoming'}>{exam.status === 'IN_PROGRESS' ? 'Resume' : actionLabel} <span aria-hidden="true">→</span></button></article>)}</div></section>;
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
