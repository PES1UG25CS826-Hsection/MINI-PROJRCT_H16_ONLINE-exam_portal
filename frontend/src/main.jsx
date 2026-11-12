import React from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

function App() {
  return (
    <main className="shell">
      <nav aria-label="Main navigation"><a className="brand" href="/">EXAM<span>PORTAL</span></a><a className="signin" href="#sign-in">Sign in</a></nav>
      <section className="hero" aria-labelledby="welcome-title">
        <p className="eyebrow">ONLINE EXAMINATION</p>
        <h1 id="welcome-title">A clear space<br />to do your best.</h1>
        <p className="intro">Your exams, all in one place. Sign in to see your upcoming assessments and results.</p>
        <a className="button" id="sign-in" href="#sign-in">Get started <span aria-hidden="true">→</span></a>
      </section>
      <footer><span>Online Exam Portal</span><span>Built for focused learning</span></footer>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
