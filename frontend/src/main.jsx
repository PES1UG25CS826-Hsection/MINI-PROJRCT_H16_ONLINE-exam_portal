import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

const SESSION_KEY = 'online-exam-session';
const queueKey = (id) => `online-exam-pending:${id}`;
function loadSession() { try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); } catch { return null; } }

async function api(path, options = {}, token = null) {
  const response = await fetch(path, { ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers } });
  if (response.status === 204) return null;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.detail || body.message || `Request failed (${response.status})`);
  return body;
}

function App() {
  const [session, setSession] = useState(loadSession);
  const [tab, setTab] = useState('home');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [exams, setExams] = useState([]);
  const [results, setResults] = useState([]);
  const [attempt, setAttempt] = useState(null);
  const [answers, setAnswers] = useState({});
  const [createdExam, setCreatedExam] = useState(null);
  const [report, setReport] = useState(null);
  const [summary, setSummary] = useState(null);
  const [saveState, setSaveState] = useState('');
  const answerRef = useRef(answers);
  const contentRef = useRef(null);
  const attemptHeadingRef = useRef(null);
  answerRef.current = answers;
  const flushing = useRef(false);
  const submitting = useRef(false);
  const role = session?.user?.role;
  const request = (path, options) => api(path, options, session?.token);

  async function refresh(resumeActive = false) {
    if (!session) return;
    try {
      const [e, r] = await Promise.all([request('/api/exams'), request('/api/results')]);
      setExams(e); setResults(r);
      if (role === 'ADMIN') setSummary(await request('/api/admin/summary'));
      if (resumeActive && role === 'STUDENT') {
        const active = await request('/api/attempts/active');
        if (active) installAttempt(active);
      }
    } catch (err) { setError(err.message); }
  }
  useEffect(() => { refresh(true); }, [session?.token]);

  function installAttempt(next) {
    if (!next || next.status !== 'IN_PROGRESS') return;
    let pending = {};
    try { pending = JSON.parse(localStorage.getItem(queueKey(next.id)) || '{}'); } catch { pending = {}; }
    const restoredAnswers = Object.fromEntries(next.questions.map((question) => {
      const queued = pending[question.id];
      return [question.id, {
        answers: queued ? queued.answer : (question.answer || []),
        markedForReview: queued ? queued.markedForReview : Boolean(question.markedForReview),
      }];
    }));
    answerRef.current = restoredAnswers;
    setAnswers(restoredAnswers);
    setAttempt({ ...next, secondsLeft: next.remainingSeconds });
    setError('');
    if (navigator.onLine && Object.keys(pending).length) window.setTimeout(() => flushPending(next.id), 0);
  }

  function updateAnswers(next) {
    setAnswers(next);
    answerRef.current = next;
    if (!attempt) return;
    let pending = {};
    try { pending = JSON.parse(localStorage.getItem(queueKey(attempt.id)) || '{}'); } catch { pending = {}; }
    const updatedAt = Date.now();
    for (const question of attempt.questions) {
      const value = next[question.id] || { answers: [], markedForReview: false };
      pending[question.id] = { answer: value.answers || [], markedForReview: Boolean(value.markedForReview), updatedAt };
    }
    localStorage.setItem(queueKey(attempt.id), JSON.stringify(pending));
    setSaveState(navigator.onLine ? 'Answer saved on this device; syncing…' : 'Offline — answer saved on this device.');
    if (navigator.onLine) window.setTimeout(() => flushPending(attempt.id), 250);
  }

  async function flushPending(attemptId) {
    if (!session?.token || !navigator.onLine || flushing.current) return;
    flushing.current = true;
    try {
      while (navigator.onLine) {
        let pending = {};
        try { pending = JSON.parse(localStorage.getItem(queueKey(attemptId)) || '{}'); } catch { pending = {}; }
        const [questionId, item] = Object.entries(pending)[0] || [];
        if (!questionId) break;
        await request(`/api/attempts/${attemptId}/answers/${questionId}`, {
          method: 'PUT',
          body: JSON.stringify({ answers: item.answer || [], markedForReview: Boolean(item.markedForReview) }),
        });
        let latest = {};
        try { latest = JSON.parse(localStorage.getItem(queueKey(attemptId)) || '{}'); } catch { latest = {}; }
        if (latest[questionId]?.updatedAt === item.updatedAt) delete latest[questionId];
        localStorage.setItem(queueKey(attemptId), JSON.stringify(latest));
      }
      const pending = JSON.parse(localStorage.getItem(queueKey(attemptId)) || '{}');
      setSaveState(Object.keys(pending).length ? 'Some answers are waiting to sync.' : 'All answers saved');
    } catch (err) {
      setSaveState(navigator.onLine ? err.message : 'Offline — answers are saved on this device and will sync when you reconnect.');
    } finally {
      flushing.current = false;
    }
  }

  useEffect(() => {
    const online = () => { if (attempt) flushPending(attempt.id); };
    window.addEventListener('online', online);
    return () => window.removeEventListener('online', online);
  }, [attempt?.id, session?.token]);
  useEffect(() => {
    const target = attempt ? attemptHeadingRef.current : session ? contentRef.current : null;
    target?.focus();
  }, [session?.token, attempt?.id]);

  async function saveAnswers() {
    if (!attempt) return;
    await flushPending(attempt.id);
  }
  useEffect(() => {
    if (!attempt) return undefined;
    const timer = window.setInterval(() => setAttempt((old) => old ? { ...old, secondsLeft: Math.max(0, (old.secondsLeft ?? old.remainingSeconds ?? 0) - 1) } : old), 1000);
    const autosave = window.setInterval(() => flushPending(attempt.id), 15000);
    return () => { window.clearInterval(timer); window.clearInterval(autosave); };
  }, [attempt?.id]);

  useEffect(() => {
    if (!attempt || attempt.status !== 'IN_PROGRESS' || attempt.secondsLeft > 0 || submitting.current) return;
    submitting.current = true;
    (async () => {
      try {
        await saveAnswers();
        const result = await request(`/api/attempts/${attempt.id}/submit`, { method: 'POST' });
        localStorage.removeItem(queueKey(attempt.id));
        setAttempt(null);
        setNotice(`Time ended. Submitted: ${result.score}/${result.maxScore}.`);
        await refresh();
      } catch (err) { setError(err.message); }
      finally { submitting.current = false; }
    })();
  }, [attempt?.id, attempt?.secondsLeft]);

  async function start(exam) {
    try {
      const next = await request(`/api/exams/${exam.id}/attempts`, { method: 'POST' });
      installAttempt(next);
      localStorage.setItem('online-exam-attempt', JSON.stringify({ id: next.id, examTitle: next.examTitle })); setError('');
    } catch (err) { setError(err.message); }
  }
  async function submit() {
    if (!attempt || !window.confirm('Submit this exam? You cannot change your answers afterward.')) return;
    try {
      await saveAnswers(); const result = await request(`/api/attempts/${attempt.id}/submit`, { method: 'POST' });
      localStorage.removeItem('online-exam-attempt'); localStorage.removeItem(queueKey(attempt.id)); setAttempt(null);
      setNotice(`Submitted: ${result.score}/${result.maxScore} (${result.percentage}%). ${result.passed ? 'Pass' : 'Fail'}.`); await refresh();
    } catch (err) { setError(err.message); }
  }
  async function signOut() {
    try { await request('/api/auth/logout', { method: 'POST' }); } catch { /* Clear the browser copy even when the server is unavailable. */ }
    localStorage.removeItem(SESSION_KEY); localStorage.removeItem('online-exam-attempt'); setSession(null); setAttempt(null);
  }
  if (!session) return <Auth onSuccess={(value) => { localStorage.setItem(SESSION_KEY, JSON.stringify(value)); setSession(value); setNotice('Welcome to the Online Exam Portal.'); setError(''); }} />;
  if (attempt) return <AttemptView attempt={attempt} answers={answers} onChange={updateAnswers} onSave={() => saveAnswers().then(() => setNotice('Answers saved.')).catch((err) => setError(err.message))} onSubmit={submit} error={error} saveState={saveState} headingRef={attemptHeadingRef} />;

  return <><a className="skip-link" href="#main-content">Skip to main content</a><main className="app-shell">
    <header className="topbar"><a className="brand" href="#home" onClick={() => setTab('home')}>EXAM<span>PORTAL</span></a><div className="account"><span>{session.user.email}</span><span className="role-pill">{role}</span><button className="quiet" onClick={signOut}>Sign out</button></div></header>
    <nav className="tabs" aria-label="Portal sections"><button className={tab === 'home' ? 'selected' : ''} onClick={() => setTab('home')}>Dashboard</button>{role === 'STUDENT' && <button className={tab === 'results' ? 'selected' : ''} onClick={() => setTab('results')}>My results</button>}{role === 'TEACHER' && <><button className={tab === 'create' ? 'selected' : ''} onClick={() => setTab('create')}>Create exam</button><button className={tab === 'questions' ? 'selected' : ''} onClick={() => setTab('questions')}>Question bank</button></>}{(role === 'TEACHER'||role === 'ADMIN') && <button className={tab === 'groups' ? 'selected' : ''} onClick={() => setTab('groups')}>Student groups</button>}{role === 'ADMIN' && <button className={tab === 'users' ? 'selected' : ''} onClick={() => setTab('users')}>Users</button>}</nav>
    <section ref={contentRef} className="content" id="main-content" tabIndex="-1">
      {notice && <div className="notice" role="status">{notice}<button aria-label="Dismiss notice" onClick={() => setNotice('')}>×</button></div>}
      {error && <div className="error" role="alert">{error}<button aria-label="Dismiss error" onClick={() => setError('')}>×</button></div>}
      {tab === 'home' && role === 'STUDENT' && <StudentDashboard exams={exams} results={results} onStart={start} />}
      {tab === 'home' && role === 'TEACHER' && <TeacherDashboard exams={exams} request={request} onReport={setReport} onRefresh={refresh} />}
      {tab === 'home' && role === 'ADMIN' && <AdminDashboard summary={summary} />}
      {tab === 'results' && <Results results={results} />}
      {tab === 'create' && <ExamBuilder request={request} exams={exams} createdExam={createdExam} setCreatedExam={setCreatedExam} onRefresh={refresh} setNotice={setNotice} setError={setError} />}
      {tab === 'groups' && (role === 'TEACHER'||role === 'ADMIN') && <GroupManager request={request} />}
      {tab === 'questions' && role === 'TEACHER' && <QuestionBankManager request={request} />}
      {tab === 'users' && <UserManager request={request} />}
      {report && <ReportModal data={report} onClose={() => setReport(null)} />}
    </section>
    <footer className="footer">Online Exam Portal <span>Exam information and results are saved by the server.</span></footer>
  </main></>;
}

function Auth({ onSuccess }) {
  const [mode, setMode] = useState('login'); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(''); const data = new FormData(event.currentTarget);
    const body = mode === 'login' ? { email: data.get('email'), password: data.get('password') } : { fullName: data.get('fullName'), email: data.get('email'), password: data.get('password') };
    try { onSuccess(await api(`/api/auth/${mode === 'login' ? 'login' : 'register'}`, { method: 'POST', body: JSON.stringify(body) })); }
    catch (err) { setError(err.message); } finally { setBusy(false); }
  }
  return <><a className="skip-link" href="#auth-main">Skip to sign in</a><main className="auth-page"><section className="auth-card" id="auth-main" tabIndex="-1"><a className="brand" href="#">EXAM<span>PORTAL</span></a><p className="eyebrow">ONLINE EXAMINATION</p><h1>{mode === 'login' ? 'Welcome back.' : 'Create your student account.'}</h1><p className="muted">Sign in to access scheduled exams and results.</p>
    {error && <div className="error" role="alert">{error}</div>}<form onSubmit={submit} className="form-stack">{mode === 'register' && <label>Full name<input name="fullName" autoComplete="name" required maxLength="160" /></label>}<label>Email<input name="email" type="email" autoComplete="email" required maxLength="254" /></label><label>Password<input name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength="8" required /></label>{mode === 'register' && <small>Use at least 8 characters with an uppercase letter, number, and symbol.</small>}<button className="primary" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button></form>
    <p className="switch-auth">{mode === 'login' ? 'New student?' : 'Already registered?'} <button className="link-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>{mode === 'login' ? 'Create an account' : 'Sign in'}</button></p></section></main></>;
}

function StudentDashboard({ exams, results, onStart }) {
  return <><div className="page-heading"><div><p className="eyebrow">STUDENT DASHBOARD</p><h1>Your exams</h1><p className="muted">Exams scheduled for your student account.</p></div><div className="stat"><strong>{results.length}</strong><span>completed</span></div></div>
    {['Available', 'Upcoming', 'Completed'].map((group) => { const items = exams.filter((e) => e.state === group); return <section className="panel" key={group}><h2>{group}</h2>{!items.length ? <p className="empty">No {group.toLowerCase()} exams.</p> : <div className="exam-grid">{items.map((e) => <article className="exam-card" key={e.id}><div className="card-top"><span className="subject">{e.subject}</span><span>{e.durationMinutes} min</span></div><h3>{e.title}</h3><p>{new Date(e.startsAt).toLocaleString()}</p>{group === 'Available' && <button className="primary" onClick={() => onStart(e)}>Start exam</button>}</article>)}</div>}</section>; })}</>;
}

function TeacherDashboard({ exams, request, onReport, onRefresh }) {
  async function publish(exam) { if (!window.confirm(`Publish “${exam.title}”?`)) return; try { await request(`/api/exams/${exam.id}/publish`, { method: 'POST' }); await onRefresh(); } catch (err) { window.alert(err.message); } }
  async function csv(exam) { try { const s=loadSession();const res=await fetch(`/api/reports/exams/${exam.id}.csv`,{headers:{Authorization:`Bearer ${s.token}`}});if(!res.ok)throw new Error('Could not download results.');const url=URL.createObjectURL(await res.blob());const a=document.createElement('a');a.href=url;a.download='exam-results.csv';a.click();URL.revokeObjectURL(url); } catch(err) { window.alert(err.message); } }
  return <><div className="page-heading"><div><p className="eyebrow">TEACHER DASHBOARD</p><h1>Exam workspace</h1><p className="muted">Create a draft, add questions, then publish during its schedule.</p></div></div><section className="panel"><h2>Your exams</h2>{!exams.length?<p className="empty">No exams yet. Use Create exam to get started.</p>:<div className="table-wrap"><table><thead><tr><th>Exam</th><th>Subject</th><th>Status</th><th>Schedule</th><th>Action</th></tr></thead><tbody>{exams.map((e)=><tr key={e.id}><td>{e.title}</td><td>{e.subject}</td><td><span className="status">{e.status}</span></td><td>{new Date(e.startsAt).toLocaleString()}</td><td className="actions">{e.status==='DRAFT'&&<button className="small-primary" onClick={()=>publish(e)}>Publish</button>}<button className="text-action" onClick={async()=>{try{onReport(await request(`/api/reports/exams/${e.id}`));}catch(err){window.alert(err.message);}}}>Report</button><button className="text-action" onClick={()=>csv(e)}>CSV</button></td></tr>)}</tbody></table></div>}</section></>;
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

function AttemptView({ attempt, answers, onChange, onSave, onSubmit, error, saveState, headingRef }) {
  const [current,setCurrent]=useState(0);const q=attempt.questions[current];const total=attempt.secondsLeft??Math.max(0,Math.ceil((new Date(attempt.expiresAt)-Date.now())/1000));const mins=Math.floor(total/60);const secs=total%60;
  function choose(option,multi){const old=answers[q.id]||{answers:[],markedForReview:false};const values=multi?(old.answers.includes(option)?old.answers.filter(x=>x!==option):[...old.answers,option]):[option];onChange({...answers,[q.id]:{...old,answers:values}});}
  function mark(){const old=answers[q.id]||{answers:[],markedForReview:false};onChange({...answers,[q.id]:{...old,markedForReview:!old.markedForReview}});}
  return <main className="attempt-page"><header className="attempt-top"><div><p className="eyebrow">EXAM IN PROGRESS</p><h1 ref={headingRef} tabIndex="-1">{attempt.examTitle}</h1></div><div className={`timer ${total<120?'urgent':''}`} aria-live="polite">{String(mins).padStart(2,'0')}:{String(secs).padStart(2,'0')}<small>remaining</small></div></header>{error&&<div className="error" role="alert">{error}</div>}{saveState&&<p className="muted" aria-live="polite" role="status">{saveState}</p>}
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
