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
  const attemptHeadingRef = useRef(null);
  const resultHeadingRef = useRef(null);
  const dashboardHeadingRef = useRef(null);

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
    if (!session) return;
    const target = attempt ? attemptHeadingRef.current : result ? resultHeadingRef.current : dashboardHeadingRef.current;
    target?.focus();
  }, [session?.accessToken, attempt?.id, Boolean(result)]);
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
    localStorage.removeItem(SESSION_KEY);
    if (attempt) localStorage.removeItem(queueKey(attempt.id));
    setSession(null); setAttempt(null); setResult(null); setGroups({ upcoming: [], available: [], completed: [] });
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

  function toggleOption(question, option) {
    const selected = Array.isArray(question.answer) ? question.answer : [];
    const next = selected.includes(option) ? selected.filter((item) => item !== option) : [...selected, option];
    updateAnswer(question, next);
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

  const unanswered = useMemo(() => attempt?.questions.filter((q) => q.answer === null || q.answer === undefined || (Array.isArray(q.answer) && q.answer.length === 0) || q.answer === '').length || 0, [attempt]);
  const formatTime = (seconds) => `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;

  if (!session) return <main className="auth-shell">
    <a className="brand" href="#home">EXAM<span>PORTAL</span></a>
    <div className="auth-card" id="main-content" tabIndex="-1">
      <p className="eyebrow">ONLINE EXAMINATION</p>
      <h1>{authMode === 'login' ? 'Welcome back.' : 'Create your student account.'}</h1>
      <p className="muted">{authMode === 'login' ? 'Sign in to see your assessments and results.' : 'Your exams and progress, all in one place.'}</p>
      <form onSubmit={authenticate} className="auth-form">
        {authMode === 'register' && <label>Full name<input name="fullName" autoComplete="name" required maxLength="160" /></label>}
        <label>Email address<input name="email" type="email" autoComplete="email" required /></label>
        <label>Password<input name="password" type="password" autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} minLength="10" required /></label>
        {authMode === 'register' && <p className="muted">Use 10 or more characters with an uppercase letter, a number, and a symbol.</p>}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={busy}>{busy ? 'Please wait…' : authMode === 'login' ? 'Sign in' : 'Create account'} <span aria-hidden="true">→</span></button>
      </form>
      <p className="switch-auth">{authMode === 'login' ? 'New to the portal?' : 'Already have an account?'} <button className="text-button" onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setError(''); }}>{authMode === 'login' ? 'Create an account' : 'Sign in'}</button></p>
    </div>
    <footer><span>Online Exam Portal</span><span>Built for focused learning</span></footer>
  </main>;

  return <main className="app-shell" id="main-content" tabIndex="-1">
    <nav className="topbar"><a className="brand" href="#home">EXAM<span>PORTAL</span></a><div className="user-tools"><span>{session.user.name}</span><button className="quiet-button" onClick={signOut}>Sign out</button></div></nav>
    {error && <div className="banner error-banner" role="alert">{error}<button onClick={() => setError('')} aria-label="Dismiss">×</button></div>}
    {notice && <div className="banner success-banner" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Dismiss">×</button></div>}
    {attempt ? <section className="exam-view" aria-label="Exam attempt">
      <header className="exam-header"><div><p className="eyebrow">EXAM IN PROGRESS</p><h1 ref={attemptHeadingRef} tabIndex="-1">Assessment</h1><p className="muted">Your answers save as you work. You can return to any question.</p></div><div className={`timer ${remaining < 300 ? 'urgent' : ''}`} aria-live="polite"><span>TIME REMAINING</span><strong>{formatTime(remaining)}</strong></div></header>
      <div className="exam-layout">
        <aside className="question-nav"><h2>Questions</h2><p className="muted small">{attempt.questions.length - unanswered} of {attempt.questions.length} answered</p><div className="question-grid">{attempt.questions.map((q, i) => <button key={q.id} aria-label={`Question ${i + 1}${q.answer != null ? ', answered' : ', unanswered'}${q.marked_for_review ? ', marked for review' : ''}`} aria-current={activeQuestion === i ? 'step' : undefined} className={`question-chip ${activeQuestion === i ? 'current' : ''} ${q.answer != null ? 'answered' : ''} ${q.marked_for_review ? 'review' : ''}`} onClick={() => setActiveQuestion(i)}>{i + 1}</button>)}</div><div className="legend"><span><i className="dot done" /> Answered</span><span><i className="dot flag" /> Review</span><span><i className="dot" /> Unanswered</span></div></aside>
        <article className="question-card">
          {attempt.questions[activeQuestion] ? <>
            <div className="question-meta"><span>QUESTION {activeQuestion + 1} OF {attempt.questions.length}</span><span>{attempt.questions[activeQuestion].points} {Number(attempt.questions[activeQuestion].points) === 1 ? 'point' : 'points'}</span></div>
            <h2>{attempt.questions[activeQuestion].prompt}</h2>
            <div className="answer-options">{(attempt.questions[activeQuestion].options || []).map((option, i) => {
              const value = typeof option === 'object' ? option.id ?? option.value ?? option.text : option;
              const label = typeof option === 'object' ? option.text ?? option.label ?? option.value : option;
              const question = attempt.questions[activeQuestion];
              const multiSelect = question.question_type === 'MULTI_SELECT';
              const selected = multiSelect ? (Array.isArray(question.answer) && question.answer.includes(value)) : question.answer === value;
              return <label key={String(value)} className={`option ${selected ? 'selected' : ''}`}><input type={multiSelect ? 'checkbox' : 'radio'} name={question.id} checked={selected} onChange={() => multiSelect ? toggleOption(question, value) : updateAnswer(question, value)} /><span className="option-letter">{String.fromCharCode(65 + i)}</span><span>{label}</span></label>;
            })}</div>
            {attempt.questions[activeQuestion].question_type === 'SHORT_ANSWER' && <label className="short-answer">Your answer<textarea rows="4" value={attempt.questions[activeQuestion].answer ?? ''} onChange={(event) => updateAnswer(attempt.questions[activeQuestion], event.target.value)} /></label>}
            <div className="question-actions"><button className={`review-button ${attempt.questions[activeQuestion].marked_for_review ? 'marked' : ''}`} onClick={() => updateAnswer(attempt.questions[activeQuestion], attempt.questions[activeQuestion].answer, !attempt.questions[activeQuestion].marked_for_review)}>{attempt.questions[activeQuestion].marked_for_review ? '★ Marked for review' : '☆ Mark for review'}</button><span className="save-status" aria-live="polite">{saveState}</span></div>
            <div className="pager"><button className="secondary" disabled={activeQuestion === 0} onClick={() => setActiveQuestion((n) => n - 1)}>← Previous</button><button className="primary" onClick={() => activeQuestion < attempt.questions.length - 1 ? setActiveQuestion((n) => n + 1) : submitAttempt(false)}>{activeQuestion < attempt.questions.length - 1 ? 'Next question →' : 'Review and submit'}</button></div>
          </> : <p>This exam has no questions.</p>}
        </article>
      </div>
      <div className="submit-row"><span>{unanswered ? `${unanswered} unanswered question${unanswered === 1 ? '' : 's'}` : 'You have answered every question'}</span><button className="danger-button" disabled={busy} onClick={() => submitAttempt(false)}>Submit exam</button></div>
    </section> : result ? <section className="result-card"><div className="result-icon">✓</div><p className="eyebrow">EXAM COMPLETE</p><h1 ref={resultHeadingRef} tabIndex="-1">{result.passed ? 'Well done.' : 'Assessment submitted.'}</h1><p className="muted">Your result has been recorded.</p><div className="score-line"><strong>{result.score}<small> / {result.maxScore}</small></strong><span>{result.percentage}%</span></div><span className={`result-pill ${result.passed ? 'pass' : 'fail'}`}>{result.passed ? 'PASS' : 'NOT PASSED'}</span>{result.questions?.length > 0 && <section className="review-results" aria-label="Question review"><h2>Question review</h2>{result.questions.map((question,index) => <article key={question.question_id}><div className="review-result-heading"><strong>Question {index + 1}</strong><span className={question.is_correct ? 'answer-correct' : 'answer-incorrect'}>{question.is_correct ? 'Correct' : 'Review'}</span></div><p>{question.prompt}</p><dl><div><dt>Your answer</dt><dd>{formatAnswer(question.answer)}</dd></div><div><dt>Correct answer</dt><dd>{formatAnswer(question.correct_answer)}</dd></div></dl></article>)}</section>}<div className="result-footer"><button className="primary" onClick={() => { setResult(null); loadExams(); }}>Back to my exams</button></div></section> : <section className="dashboard">
      <div className="dashboard-heading"><div><p className="eyebrow">STUDENT DASHBOARD</p><h1 ref={dashboardHeadingRef} tabIndex="-1">Your exams, at a glance.</h1><p className="muted">Pick up where you left off, or see what’s coming next.</p></div><button className="quiet-button" onClick={() => loadExams().catch((err) => setError(err.message))}>Refresh ↻</button></div>
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

function ExamBuilder({ request, exams, createdExam, setCreatedExam, onRefresh, setNotice, setError }) {
  const [saving,setSaving]=useState(false);
  const [groups,setGroups]=useState([]);const [assignedGroups,setAssignedGroups]=useState([]);const [groupToAssign,setGroupToAssign]=useState('');const [bankQuestions,setBankQuestions]=useState([]);
  useEffect(()=>{request('/api/groups').then(setGroups).catch(err=>setError(err.message));},[]);
  useEffect(()=>{request('/api/questions').then(setBankQuestions).catch(err=>setError(err.message));},[]);
  useEffect(()=>{if(createdExam)request(`/api/exams/${createdExam}/groups`).then(setAssignedGroups).catch(err=>setError(err.message));},[createdExam]);
  async function create(event) { event.preventDefault();setSaving(true);setError('');const f=new FormData(event.currentTarget);try{const created=await request('/api/exams',{method:'POST',body:JSON.stringify({title:f.get('title'),subject:f.get('subject'),durationMinutes:Number(f.get('durationMinutes')),startsAt:new Date(f.get('startsAt')).toISOString(),endsAt:new Date(f.get('endsAt')).toISOString(),totalMarks:Number(f.get('totalMarks')),passingMarks:Number(f.get('passingMarks')),negativeMarkPercent:Number(f.get('negativeMarkPercent')),maxAttempts:Number(f.get('maxAttempts')),showAnswersAfterSubmit:f.get('showAnswersAfterSubmit')==='on',randomizeQuestions:f.get('randomizeQuestions')==='on',randomizeOptions:f.get('randomizeOptions')==='on'})});setCreatedExam(created.id);setNotice('Draft created. Add questions, then publish it from your dashboard.');await onRefresh();}catch(err){setError(err.message);}finally{setSaving(false);} }
  async function addQuestion(event) { event.preventDefault();setError('');const f=new FormData(event.currentTarget);const type=f.get('type');const options=type==='TRUE_FALSE'?['True','False']:String(f.get('options')).split('\n').map(x=>x.trim()).filter(Boolean);const correctAnswers=String(f.get('correctAnswers')).split(',').map(x=>x.trim()).filter(Boolean);try{await request(`/api/exams/${createdExam}/questions`,{method:'POST',body:JSON.stringify({prompt:f.get('prompt'),type,options,correctAnswers,points:Number(f.get('points'))})});event.currentTarget.reset();setNotice('Question added. Add another or publish this draft from the dashboard.');await onRefresh();}catch(err){setError(err.message);} }
  async function assignGroup(event){event.preventDefault();if(!groupToAssign)return;try{await request(`/api/exams/${createdExam}/groups/${groupToAssign}`,{method:'PUT'});setAssignedGroups(await request(`/api/exams/${createdExam}/groups`));setNotice('Student group assigned to this draft.');}catch(err){setError(err.message);}}
  async function addBankQuestion(question){try{await request(`/api/exams/${createdExam}/question-bank/${question.id}`,{method:'POST',body:JSON.stringify({pointsOverride:Number(question.points)})});setNotice('Question added from the question bank.');}catch(err){setError(err.message);}}
  return <><div className="page-heading"><div><p className="eyebrow">EXAM BUILDER</p><h1>Create an exam</h1><p className="muted">Set the schedule and marks before adding questions.</p></div></div>
    <section className="panel"><form className="form-grid" onSubmit={create}><label>Exam title<input name="title" required maxLength="200" /></label><label>Subject<input name="subject" required maxLength="160" /></label><label>Duration (5–240 min)<input name="durationMinutes" type="number" min="5" max="240" defaultValue="30" required /></label><label>Total marks<input name="totalMarks" type="number" min="1" step="0.25" defaultValue="10" required /></label><label>Passing marks<input name="passingMarks" type="number" min="0" step="0.25" defaultValue="4" required /></label><label>Negative marking (%)<input name="negativeMarkPercent" type="number" min="0" max="100" defaultValue="0" /></label><label>Start time<input name="startsAt" type="datetime-local" required /></label><label>End time<input name="endsAt" type="datetime-local" required /></label><label>Attempt limit<input name="maxAttempts" type="number" min="1" max="10" defaultValue="1" /></label><div className="check-stack"><label className="check"><input name="showAnswersAfterSubmit" type="checkbox" /> Show correct answers after submission</label><label className="check"><input name="randomizeQuestions" type="checkbox" /> Randomize questions</label><label className="check"><input name="randomizeOptions" type="checkbox" /> Randomize options</label></div><button className="primary" disabled={saving}>{saving?'Saving…':'Save draft'}</button></form></section>
    {createdExam&&<><section className="panel"><p className="eyebrow">DRAFT QUESTION EDITOR</p><h2>Add questions</h2><form className="form-grid" onSubmit={addQuestion}><label className="wide">Question prompt<textarea name="prompt" rows="3" required maxLength="4000" /></label><label>Question type<select name="type"><option value="MCQ">Single-choice MCQ</option><option value="MULTI_SELECT">Multiple-select MCQ</option><option value="TRUE_FALSE">True / False</option></select></label><label>Marks<input name="points" type="number" min="1" step="0.25" defaultValue="1" required /></label><label>Options, one per line<textarea name="options" rows="4" placeholder={'Option A\nOption B\nOption C'} /></label><label>Correct answer text<input name="correctAnswers" required placeholder="Option B; for multi-select, separate with commas" /></label><button className="primary">Add question</button></form></section><section className="panel"><h2>Assign student groups</h2><p className="muted">A draft needs at least one assigned group before it can be published.</p><form className="inline-form" onSubmit={assignGroup}><label>Group<select value={groupToAssign} onChange={e=>setGroupToAssign(e.target.value)} required><option value="">Choose a group</option>{groups.map(g=><option key={g.id} value={g.id}>{g.name}</option>)}</select></label><button className="secondary" disabled={!groupToAssign}>Assign group</button></form>{assignedGroups.length>0&&<ul>{assignedGroups.map(g=><li key={g.id}>{g.name}</li>)}</ul>}{groups.length===0&&<p className="empty">Create a group in Student groups before publishing this exam.</p>}</section></>}
    {createdExam&&<section className="panel"><h2>Select from your question bank</h2>{bankQuestions.length===0?<p className="empty">Your question bank is empty. Create questions from the Question bank tab or add one above.</p>:<ul className="bank-list">{bankQuestions.map(q=><li key={q.id}><span><strong>{q.prompt}</strong><small>{q.type} · {q.points} mark(s)</small></span><button className="small-primary" onClick={()=>addBankQuestion(q)}>Add to draft</button></li>)}</ul>}</section>}
    <section className="panel"><h2>Draft and published exams</h2><p className="muted">{exams.length} exam(s). Question marks must add up to the exam total before publishing.</p></section>
  </>;
}

function QuestionBankManager({ request }) {
  const blank={prompt:'',type:'MCQ',options:'Option A\nOption B',correctAnswers:'',points:'1'};
  const [items,setItems]=useState([]);const [draft,setDraft]=useState(blank);const [editingId,setEditingId]=useState(null);const [message,setMessage]=useState('');
  async function load(){setItems(await request('/api/questions'));}
  useEffect(()=>{load().catch(err=>setMessage(err.message));},[]);
  function field(name,value){setDraft(old=>({...old,[name]:value}));}
  async function save(event){event.preventDefault();const options=draft.type==='TRUE_FALSE'?['True','False']:draft.options.split('\n').map(x=>x.trim()).filter(Boolean);const correctAnswers=draft.correctAnswers.split(',').map(x=>x.trim()).filter(Boolean);const body={...draft,options,correctAnswers,points:Number(draft.points)};try{await request(editingId?`/api/questions/${editingId}`:'/api/questions',{method:editingId?'PUT':'POST',body:JSON.stringify(body)});setDraft(blank);setEditingId(null);await load();setMessage('Question saved.');}catch(err){setMessage(err.message);}}
  function edit(q){setEditingId(q.id);setDraft({prompt:q.prompt,type:q.type,options:q.options.join('\n'),correctAnswers:q.correctAnswers.join(', '),points:String(q.points)});}
  async function remove(id){if(!window.confirm('Delete this question? Questions already used in exams cannot be deleted.'))return;try{await request(`/api/questions/${id}`,{method:'DELETE'});await load();setMessage('Question deleted.');}catch(err){setMessage(err.message);}}
  return <><div className="page-heading"><div><p className="eyebrow">TEACHER TOOLS</p><h1>Question bank</h1><p className="muted">Create reusable questions, then select them while building a draft exam.</p></div></div>{message&&<div className="notice" role="status">{message}</div>}<section className="panel"><h2>{editingId?'Edit question':'Create question'}</h2><form className="form-grid" onSubmit={save}><label className="wide">Prompt<textarea value={draft.prompt} onChange={e=>field('prompt',e.target.value)} maxLength="4000" required /></label><label>Type<select value={draft.type} onChange={e=>field('type',e.target.value)}><option value="MCQ">Single-choice MCQ</option><option value="MULTI_SELECT">Multiple-select MCQ</option><option value="TRUE_FALSE">True / False</option></select></label><label>Marks<input type="number" min="0.01" step="0.25" value={draft.points} onChange={e=>field('points',e.target.value)} required /></label>{draft.type!=='TRUE_FALSE'&&<label>Options, one per line<textarea value={draft.options} onChange={e=>field('options',e.target.value)} required /></label>}<label>Correct answer text<input value={draft.correctAnswers} onChange={e=>field('correctAnswers',e.target.value)} required placeholder="Use commas for multi-select" /></label><div className="button-row"><button className="primary">{editingId?'Save changes':'Add to question bank'}</button>{editingId&&<button type="button" className="secondary" onClick={()=>{setDraft(blank);setEditingId(null);}}>Cancel edit</button>}</div></form></section><section className="panel"><h2>Saved questions</h2>{items.length===0?<p className="empty">No questions yet.</p>:<ul className="bank-list">{items.map(q=><li key={q.id}><span><strong>{q.prompt}</strong><small>{q.type} · {q.points} mark(s)</small></span><div className="button-row"><button className="text-action" onClick={()=>edit(q)}>Edit</button><button className="text-action" onClick={()=>remove(q.id)}>Delete</button></div></li>)}</ul>}</section></>;
}

function GroupManager({ request }) {
  const [groups,setGroups]=useState([]);const [selected,setSelected]=useState('');const [members,setMembers]=useState([]);const [message,setMessage]=useState('');
  async function load(){const rows=await request('/api/groups');setGroups(rows);if(!selected&&rows.length)setSelected(rows[0].id);}
  useEffect(()=>{load().catch(err=>setMessage(err.message));},[]);
  useEffect(()=>{if(selected)request(`/api/groups/${selected}/members`).then(setMembers).catch(err=>setMessage(err.message));else setMembers([]);},[selected]);
  async function create(event){event.preventDefault();const f=new FormData(event.currentTarget);try{const row=await request('/api/groups',{method:'POST',body:JSON.stringify({name:f.get('name'),description:f.get('description')})});event.currentTarget.reset();await load();setSelected(row.id);setMessage('Student group created.');}catch(err){setMessage(err.message);}}
  async function add(event){event.preventDefault();if(!selected)return;const f=new FormData(event.currentTarget);try{await request(`/api/groups/${selected}/members`,{method:'POST',body:JSON.stringify({email:f.get('email')})});event.currentTarget.reset();setMembers(await request(`/api/groups/${selected}/members`));setMessage('Student added to the group.');}catch(err){setMessage(err.message);}}
  async function remove(studentId){try{await request(`/api/groups/${selected}/members/${studentId}`,{method:'DELETE'});setMembers(await request(`/api/groups/${selected}/members`));setMessage('Student removed from the group.');}catch(err){setMessage(err.message);}}
  return <><div className="page-heading"><div><p className="eyebrow">ELIGIBILITY</p><h1>Student groups</h1><p className="muted">Create class groups, add registered students, then assign groups to draft exams.</p></div></div>{message&&<div className="notice" role="status">{message}</div>}<section className="panel"><h2>Create group</h2><form className="form-grid" onSubmit={create}><label>Group name<input name="name" maxLength="120" required /></label><label>Description<input name="description" maxLength="500" /></label><button className="primary">Create group</button></form></section><section className="panel"><h2>Manage members</h2>{groups.length===0?<p className="empty">Create a group to add students.</p>:<><form className="inline-form" onSubmit={add}><label>Group<select value={selected} onChange={e=>setSelected(e.target.value)}>{groups.map(g=><option key={g.id} value={g.id}>{g.name} ({g.memberCount})</option>)}</select></label><label>Student email<input name="email" type="email" required /></label><button className="secondary">Add student</button></form><ul className="member-list">{members.map(m=><li key={m.id}>{m.fullName} <span>{m.email}</span><button className="text-action" onClick={()=>remove(m.id)}>Remove</button></li>)}</ul></>}</section></>;
}

function AttemptView({ attempt, answers, onChange, onSave, onSubmit, error }) {
  const [current,setCurrent]=useState(0);const q=attempt.questions[current];const total=attempt.secondsLeft??Math.max(0,Math.ceil((new Date(attempt.expiresAt)-Date.now())/1000));const mins=Math.floor(total/60);const secs=total%60;
  function choose(option,multi){const old=answers[q.id]||{answers:[],markedForReview:false};const values=multi?(old.answers.includes(option)?old.answers.filter(x=>x!==option):[...old.answers,option]):[option];onChange({...answers,[q.id]:{...old,answers:values}});}
  function mark(){const old=answers[q.id]||{answers:[],markedForReview:false};onChange({...answers,[q.id]:{...old,markedForReview:!old.markedForReview}});}
  return <main className="attempt-page"><header className="attempt-top"><div><p className="eyebrow">EXAM IN PROGRESS</p><h1>{attempt.examTitle}</h1></div><div className={`timer ${total<120?'urgent':''}`} aria-live="polite">{String(mins).padStart(2,'0')}:{String(secs).padStart(2,'0')}<small>remaining</small></div></header>{error&&<div className="error" role="alert">{error}</div>}
    <div className="attempt-layout"><aside className="question-nav"><h2>Questions</h2><div className="number-grid">{attempt.questions.map((item,i)=><button key={item.id} className={`${i===current?'current':''} ${answers[item.id]?.answers?.length?'answered':''} ${answers[item.id]?.markedForReview?'marked':''}`} onClick={()=>setCurrent(i)} aria-label={`Question ${i+1}`}>{i+1}</button>)}</div><p className="legend"><span className="answered-dot"/> Answered <span className="marked-dot"/> Review</p></aside>
      <section className="question-card"><div className="card-top"><span>Question {current+1} of {attempt.questions.length}</span><span>{q.points} mark(s)</span></div><h2>{q.prompt}</h2><div className="choice-list">{q.options.map((option)=><label key={option} className={`choice ${(answers[q.id]?.answers||[]).includes(option)?'chosen':''}`}><input type={q.type==='MULTI_SELECT'?'checkbox':'radio'} name={`question-${q.id}`} checked={(answers[q.id]?.answers||[]).includes(option)} onChange={()=>choose(option,q.type==='MULTI_SELECT')} />{option}</label>)}</div><div className="question-actions"><button className="quiet" onClick={mark}>{answers[q.id]?.markedForReview?'Remove review mark':'Mark for review'}</button><button className="quiet" onClick={onSave}>Save now</button></div><div className="pager"><button className="secondary" disabled={current===0} onClick={()=>setCurrent(current-1)}>Previous</button><button className="secondary" disabled={current===attempt.questions.length-1} onClick={()=>setCurrent(current+1)}>Next question</button></div></section></div>
    <footer className="submit-bar"><span>{Object.values(answers).filter(v=>v.answers?.length).length} answered · {Object.values(answers).filter(v=>v.markedForReview).length} for review</span><button className="danger-button" onClick={onSubmit}>Submit exam</button></footer></main>;
}

function Results({ results }) { return <><div className="page-heading"><div><p className="eyebrow">RESULTS</p><h1>My results</h1></div></div><section className="panel"><h2>Submitted attempts</h2>{!results.length?<p className="empty">Your exam results will appear here.</p>:<div className="table-wrap"><table><thead><tr><th>Exam</th><th>Score</th><th>Percentage</th><th>Outcome</th><th>Submitted</th></tr></thead><tbody>{results.map(r=><tr key={r.id}><td>{r.examTitle}</td><td>{r.score}/{r.maxScore}</td><td>{r.percentage}%</td><td><span className={`status ${r.passed?'pass':'fail'}`}>{r.passed?'Pass':'Fail'}</span></td><td>{new Date(r.gradedAt).toLocaleString()}</td></tr>)}</tbody></table></div>}</section></>; }

function AdminDashboard({ summary }) { return <><div className="page-heading"><div><p className="eyebrow">ADMINISTRATOR</p><h1>Portal summary</h1><p className="muted">Live totals from saved records.</p></div></div><div className="stats-grid">{[['Users',summary?.users],['Exams',summary?.exams],['Attempts',summary?.attempts],['Submitted',summary?.submittedAttempts]].map(([label,value])=><article className="stat-card" key={label}><span>{label}</span><strong>{value??'—'}</strong></article>)}</div></>; }

function UserManager({ request }) {
  const [users,setUsers]=useState([]);const [message,setMessage]=useState('');
  async function load(){try{setUsers(await request('/api/admin/users'));}catch(err){setMessage(err.message);}}
  useEffect(()=>{load();},[]);
  async function create(event){event.preventDefault();const f=new FormData(event.currentTarget);try{await request('/api/admin/users',{method:'POST',body:JSON.stringify({fullName:f.get('fullName'),email:f.get('email'),password:f.get('password'),role:f.get('role')})});event.currentTarget.reset();setMessage('User created.');load();}catch(err){setMessage(err.message);}}
  return <><div className="page-heading"><div><p className="eyebrow">USER MANAGEMENT</p><h1>Accounts</h1></div></div>{message&&<div className="notice" role="status">{message}</div>}<section className="panel"><h2>Create account</h2><form className="form-grid" onSubmit={create}><label>Full name<input name="fullName" required /></label><label>Email<input name="email" type="email" required /></label><label>Temporary password<input name="password" type="password" minLength="8" required /></label><label>Role<select name="role"><option>STUDENT</option><option>TEACHER</option><option>ADMIN</option></select></label><button className="primary">Create user</button></form></section><section className="panel"><h2>Existing users</h2><div className="table-wrap"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th></tr></thead><tbody>{users.map(u=><tr key={u.id}><td>{u.fullName}</td><td>{u.email}</td><td>{u.role}</td><td>{u.active?'Active':'Inactive'}</td></tr>)}</tbody></table></div></section></>;
}

function ReportModal({ data, onClose }) { return <div className="modal-backdrop" role="presentation" onClick={onClose}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="report-title" onClick={e=>e.stopPropagation()}><button className="close" aria-label="Close report" onClick={onClose}>×</button><p className="eyebrow">EXAM REPORT</p><h2 id="report-title">Results summary</h2><div className="report-grid">{[['Attempts',data.attempts],['Average',data.average],['Highest',data.highest],['Lowest',data.lowest],['Pass rate',`${Number(data.passPercentage).toFixed(1)}%`]].map(([label,value])=><div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div></section></div>; }

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
