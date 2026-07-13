import { useEffect, useMemo, useRef, useState } from "react";
import { caseStudies, meetingTranscript, speakingStages } from "./caseStudies.js";

const asset = (path) => `../../case-studies/${path}`;

const Icon = ({ name, size = 20 }) => {
  const paths = {
    arrow: <path d="M7 17 17 7M8 7h9v9" />,
    back: <path d="m15 18-6-6 6-6" />,
    play: <path d="m9 7 8 5-8 5V7Z" />,
    pause: <path d="M9 7v10M15 7v10" />,
    replay: <path d="M4.8 8.3A8 8 0 1 1 4 14M4.8 8.3V3.8M4.8 8.3h4.5" />,
    volume: <><path d="M5 10v4h3l4 3V7L8 10H5Z" /><path d="M15 9.5a4 4 0 0 1 0 5" /></>,
    mute: <><path d="M5 10v4h3l4 3V7L8 10H5Z" /><path d="m16 10 4 4m0-4-4 4" /></>,
    check: <path d="m5 12 4 4L19 6" />,
    github: <path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.87c-2.78.6-3.37-1.18-3.37-1.18-.45-1.16-1.1-1.47-1.1-1.47-.91-.62.06-.61.06-.61 1 .07 1.53 1.03 1.53 1.03.89 1.53 2.34 1.09 2.9.83.09-.65.35-1.09.64-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.99 1.03-2.69-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.74 1.03A9.4 9.4 0 0 1 12 6.82a9.4 9.4 0 0 1 2.5.34c1.9-1.3 2.74-1.03 2.74-1.03.55 1.38.2 2.4.1 2.65.64.7 1.03 1.6 1.03 2.69 0 3.84-2.34 4.67-4.57 4.92.36.31.68.92.68 1.85v2.74c0 .27.18.59.69.49A10 10 0 0 0 12 2Z" />,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill={name === "github" ? "currentColor" : "none"} stroke={name === "github" ? "none" : "currentColor"} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
};

function ThemeButton({ theme, setTheme }) {
  return (
    <button className="case-theme" type="button" onClick={() => setTheme(theme === "dark" ? "light" : "dark")} aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}>
      <span aria-hidden="true">{theme === "dark" ? "☼" : "☾"}</span>{theme === "dark" ? "Light" : "Dark"}
    </button>
  );
}

function Waveform({ active, color = "var(--case-accent)" }) {
  return <div className={`case-wave ${active ? "is-active" : ""}`} style={{ "--wave-color": color }} aria-hidden="true">{Array.from({ length: 34 }, (_, index) => <i key={index} style={{ "--bar": `${22 + ((index * 17) % 68)}%`, "--delay": `${index * -0.037}s` }} />)}</div>;
}

function MeetingDemo() {
  const audioRef = useRef(null);
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  const [follow, setFollow] = useState(true);

  useEffect(() => {
    const player = audioRef.current;
    if (!player) return;
    player.src = asset(meetingTranscript[index].audio);
    player.muted = muted;
    if (playing) player.play().catch(() => setPlaying(false));
  }, [index]);

  useEffect(() => { if (audioRef.current) audioRef.current.muted = muted; }, [muted]);

  const togglePlay = () => {
    const player = audioRef.current;
    if (!player) return;
    if (playing) player.pause(); else player.play().catch(() => null);
    setPlaying(!playing);
  };
  const replay = () => {
    setIndex(0);
    const player = audioRef.current;
    if (player) {
      player.src = asset(meetingTranscript[0].audio);
      player.currentTime = 0;
      player.play().catch(() => null);
    }
    setPlaying(true);
  };
  const onEnded = () => {
    if (index < meetingTranscript.length - 1) setIndex((value) => value + 1);
    else setPlaying(false);
  };

  return (
    <section className="demo-shell" id="interactive-demo" aria-labelledby="meeting-demo-title">
      <div className="demo-heading"><div><span className="case-kicker">Interactive product walkthrough</span><h2 id="meeting-demo-title">A meeting becomes structured memory.</h2></div><p>Fictional voices and content. The interface demonstrates the real product flow without sending audio to a cloud model.</p></div>
      <div className="meeting-console">
        <div className="console-top"><div><span className="live-dot" /> Product launch alignment</div><span>00:04:18 · 2 speakers</span></div>
        <Waveform active={playing} />
        <div className="player-controls">
          <button type="button" onClick={togglePlay} aria-label={playing ? "Pause meeting" : "Play meeting"}><Icon name={playing ? "pause" : "play"} /></button>
          <button type="button" onClick={replay} aria-label="Replay meeting"><Icon name="replay" /></button>
          <input type="range" min="0" max={meetingTranscript.length - 1} value={index} onChange={(event) => { setIndex(Number(event.target.value)); setPlaying(false); }} aria-label="Meeting segment" />
          <button type="button" onClick={() => setMuted(!muted)} aria-label={muted ? "Unmute" : "Mute"}><Icon name={muted ? "mute" : "volume"} /></button>
          <label><input type="checkbox" checked={follow} onChange={(event) => setFollow(event.target.checked)} /> Follow transcript</label>
        </div>
        <audio ref={audioRef} onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onEnded={onEnded} />
        <div className="transcript-panel" aria-live="polite">
          {meetingTranscript.map((line, lineIndex) => (
            <button type="button" className={`transcript-line ${lineIndex === index ? "is-current" : ""} ${lineIndex > index && follow ? "is-future" : ""}`} key={line.text} onClick={() => { setIndex(lineIndex); setPlaying(false); }}>
              <span className={`speaker-avatar speaker-${lineIndex % 2}`}>{line.speaker[0]}</span>
              <span><strong>{line.speaker}<small>{line.role}</small></strong><p>{line.text}</p></span>
            </button>
          ))}
        </div>
        <div className="action-strip"><span><Icon name="check" size={16} /> Action detected</span><p>Daniel will validate transcript export and speaker-label persistence before 4:00 PM.</p></div>
      </div>
    </section>
  );
}

function IeltsDemo() {
  const promptRef = useRef(null);
  const [mode, setMode] = useState("speaking");
  const [stage, setStage] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [remaining, setRemaining] = useState(speakingStages[0].time);
  const [evaluation, setEvaluation] = useState(false);

  useEffect(() => { setRemaining(speakingStages[stage].time); setEvaluation(stage === speakingStages.length - 1); }, [stage]);
  useEffect(() => {
    if (!playing || remaining <= 0) return undefined;
    const timer = window.setInterval(() => setRemaining((value) => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [playing, remaining]);
  const playPrompt = () => {
    const player = promptRef.current;
    player.src = asset(speakingStages[stage].audio);
    player.play().catch(() => null);
  };

  return (
    <section className="demo-shell" id="interactive-demo" aria-labelledby="ielts-demo-title">
      <div className="demo-heading"><div><span className="case-kicker">Interactive product walkthrough</span><h2 id="ielts-demo-title">Try the candidate journey.</h2></div><p>A shortened, static simulation using fictional responses and authentic examiner prompts from the local application.</p></div>
      <div className="mode-tabs" role="tablist"><button className={mode === "speaking" ? "active" : ""} onClick={() => setMode("speaking")} role="tab">Speaking</button><button className={mode === "writing" ? "active" : ""} onClick={() => setMode("writing")} role="tab">Writing</button></div>
      {mode === "speaking" ? (
        <div className="ielts-console">
          <div className="test-progress">{speakingStages.map((item, index) => <button key={item.label} className={index <= stage ? "complete" : ""} onClick={() => setStage(index)}><span>{index + 1}</span>{item.label}</button>)}</div>
          <div className="examiner-card">
            <div className="examiner-orb"><img src={asset("ielts/logo.webp")} alt="IELTS Mock Examiner logo" /></div>
            <span>AI Examiner · {speakingStages[stage].label}</span>
            <h3>{speakingStages[stage].prompt}</h3>
            <Waveform active={playing} color="var(--case-accent)" />
            <audio ref={promptRef} onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onEnded={() => setPlaying(false)} />
            <div className="exam-actions"><button className="case-primary" onClick={playPrompt}><Icon name="play" size={17} /> Play examiner</button>{stage < speakingStages.length - 1 && <button className="case-secondary" onClick={() => setStage(stage + 1)}>Next stage <Icon name="arrow" size={17} /></button>}</div>
          </div>
          <div className="candidate-card"><div><span className="recording-dot" /> Candidate response</div><strong>{remaining ? `00:${String(remaining).padStart(2, "0")}` : "Complete"}</strong><button onClick={() => setPlaying(!playing)}>{playing ? "Pause timer" : "Start response"}</button></div>
          {evaluation && <ScoreReport />}
        </div>
      ) : <WritingDemo />}
    </section>
  );
}

function WritingDemo() {
  const [revealed, setRevealed] = useState(false);
  return <div className="writing-console"><div className="writing-prompt"><span>Writing Task 2 · 40 minutes</span><h3>Some people believe technology makes communication stronger, while others think it reduces meaningful connection. Discuss both views and give your opinion.</h3><textarea defaultValue="Technology can widen access to communication, but its value depends on how deliberately people use it..." aria-label="Sample IELTS essay" /></div><div className="writing-side"><span className="case-kicker">Evaluation preview</span><h3>Criterion-level feedback, not just a number.</h3><p>The production workflow sends the completed response through a configured AI evaluation service and stores the resulting feedback.</p><button className="case-primary" onClick={() => setRevealed(true)}>Show sample evaluation</button>{revealed && <ScoreReport compact />}</div></div>;
}

function ScoreReport({ compact = false }) {
  return <div className={`score-report ${compact ? "compact" : ""}`}><div className="overall-score"><span>Illustrative band</span><strong>7.0</strong></div><div className="score-grid">{[["Fluency", "7.0"], ["Vocabulary", "7.0"], ["Grammar", "6.5"], ["Coherence", "7.5"]].map(([name, score]) => <div key={name}><span>{name}</span><strong>{score}</strong><i style={{ width: `${Number(score) * 10}%` }} /></div>)}</div><p><strong>Next focus:</strong> develop examples more precisely and vary complex sentence openings.</p></div>;
}

function Gallery({ items }) {
  const [selected, setSelected] = useState(null);
  useEffect(() => {
    const close = (event) => { if (event.key === "Escape") setSelected(null); };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, []);
  return <><div className="case-gallery">{items.map((item, index) => <button key={item.src} onClick={() => setSelected(index)}><img src={asset(item.src)} alt={item.alt} loading="lazy" /><span>{item.label}</span></button>)}</div>{selected !== null && <div className="lightbox" role="dialog" aria-modal="true" aria-label={items[selected].label} onClick={() => setSelected(null)}><button autoFocus aria-label="Close image">×</button><img src={asset(items[selected].src)} alt={items[selected].alt} /></div>}</>;
}

function Architecture({ study }) {
  return <div className="architecture-flow">{study.architecture.map(([title, detail], index) => <div key={title}><span>{String(index + 1).padStart(2, "0")}</span><strong>{title}</strong><p>{detail}</p></div>)}</div>;
}

function GraduationBody({ study }) {
  return <>
    <section className="capstone-foundation case-section"><div><span className="case-kicker">Project foundation</span><h2>Practice needs feedback loops, not isolated exercises.</h2></div><div><p>IELTS learners benefit from repeated speaking and writing practice, but timely structured feedback is often difficult to access. The capstone explored how one integrated system could support both modules.</p><p>The team combined a React client, Django APIs, realtime audio transport, speech models, and configurable AI evaluation into one working product.</p></div></section>
    <section className="case-section"><div className="section-title"><span className="case-kicker">Methodology</span><h2>From research question to working system.</h2></div><div className="method-grid">{[["01", "Frame", "Define learner needs and examiner-aligned outputs."], ["02", "Design", "Map writing and realtime speaking journeys."], ["03", "Integrate", "Connect STT, TTS, evaluation, auth, and storage."], ["04", "Validate", "Exercise the complete system and document limitations."]].map(([n,t,d]) => <article key={n}><span>{n}</span><h3>{t}</h3><p>{d}</p></article>)}</div></section>
    <section className="case-section"><div className="section-title"><span className="case-kicker">System architecture</span><h2>An integrated academic prototype.</h2></div><Architecture study={study} /></section>
    <section className="case-section poster-section" id="academic-poster"><div className="poster-copy"><span className="case-kicker">Academic capstone poster</span><h2>One frame, the complete project story.</h2><p>The original poster’s strongest evidence is adapted here with corrected implementation details and explicit team credit.</p><div className="credit-list"><p><strong>Student team</strong>A. Khasakhojaev · J. Rustamov</p><p><strong>Supervisor</strong>Eugene Castro</p><p><strong>Institution</strong>Central Asian University · Engineering School</p></div></div><Gallery items={study.gallery} /></section>
  </>;
}

export default function CaseStudyApp({ projectId }) {
  const study = caseStudies[projectId] || caseStudies.meeting;
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || "dark");
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem("portfolio-theme", theme); document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "light" ? "#f4f0e8" : "#070511"); }, [theme]);
  const related = useMemo(() => Object.entries(caseStudies).filter(([key]) => key !== projectId), [projectId]);

  return <div className={`case-site accent-${study.accent}`}>
    <header className="case-nav"><a href="../../" className="case-brand"><span>RJ</span>Javlonbek Rustamov</a><nav><a href="#story">Story</a><a href={projectId === "graduation" ? "#academic-poster" : "#interactive-demo"}>{projectId === "graduation" ? "Poster" : "Demo"}</a><ThemeButton theme={theme} setTheme={setTheme} /><a className="case-back" href="../../#work"><Icon name="back" size={17} /> Portfolio</a></nav></header>
    <main>
      <section className="case-hero" id="story"><div className="case-hero-copy"><span className="case-kicker">{study.eyebrow}</span><h1>{study.title}</h1><p>{study.subtitle}</p><div className="case-actions"><a className="case-primary" href={projectId === "graduation" ? "#academic-poster" : "#interactive-demo"}>{projectId === "graduation" ? "Explore capstone" : "Try guided demo"}<Icon name="arrow" size={18} /></a><a className="case-secondary" href={study.repository} target="_blank" rel="noreferrer"><Icon name="github" size={18} /> Source code</a></div></div><div className="case-hero-art"><div className="case-orbit orbit-a" /><div className="case-orbit orbit-b" /><div className="case-emblem">{projectId === "meeting" ? "MW" : projectId === "ielts" ? "IE" : "26"}</div><Waveform active /><span>{projectId === "graduation" ? "Capstone research" : "Interactive walkthrough"}</span></div><div className="case-metrics">{study.metrics.map(([value, label]) => <div key={value}><strong>{value}</strong><span>{label}</span></div>)}</div></section>
      {projectId === "meeting" && <MeetingDemo />}
      {projectId === "ielts" && <IeltsDemo />}
      {projectId === "graduation" && <GraduationBody study={study} />}
      {projectId === "ielts" && <section className="capstone-credit"><div><span className="case-kicker">Academic provenance</span><h2>Built as a collaborative BSc capstone.</h2><p>Central Asian University Engineering School · A. Khasakhojaev and J. Rustamov · supervised by Eugene Castro.</p></div><a className="case-secondary" href="../graduation-capstone/">Explore the graduation story <Icon name="arrow" size={17} /></a></section>}
      {projectId !== "graduation" && <><section className="case-section"><div className="section-title"><span className="case-kicker">Under the hood</span><h2>A complete product pipeline.</h2></div><Architecture study={study} /></section><section className="case-section gallery-section"><div className="section-title"><span className="case-kicker">Authentic product screens</span><h2>Captured from the local application.</h2></div><Gallery items={study.gallery} /></section></>}
      <section className="case-section toolkit-section"><div><span className="case-kicker">Working system</span><h2>Technology chosen around the experience.</h2></div><div className="case-tags">{study.stack.map((item) => <span key={item}>{item}</span>)}</div></section>
      <section className="related-section"><span className="case-kicker">Continue exploring</span><div>{related.map(([key, item]) => <a href={`../${key === "meeting" ? "meeting-whisperer" : key === "ielts" ? "ielts-examiner" : "graduation-capstone"}/`} key={key}><span>{item.eyebrow}</span><strong>{item.title}</strong><Icon name="arrow" /></a>)}</div></section>
    </main>
    <footer className="case-footer"><a href="../../">RJ</a><p>Designed and built in Tashkent · Static product walkthrough</p><a href="mailto:rjavlon2004@gmail.com">rjavlon2004@gmail.com</a></footer>
  </div>;
}
