import { useEffect, useMemo, useState } from "react";
import { caseStudies } from "./caseStudies.js";

const asset = (path) => `../../case-studies/${path}`;

const Icon = ({ name, size = 20 }) => {
  const paths = {
    arrow: <path d="M7 17 17 7M8 7h9v9" />,
    back: <path d="m15 18-6-6 6-6" />,
    github: <path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.87c-2.78.6-3.37-1.18-3.37-1.18-.45-1.16-1.1-1.47-1.1-1.47-.91-.62.06-.61.06-.61 1 .07 1.53 1.03 1.53 1.03.89 1.53 2.34 1.09 2.9.83.09-.65.35-1.09.64-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.99 1.03-2.69-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.74 1.03A9.4 9.4 0 0 1 12 6.82a9.4 9.4 0 0 1 2.5.34c1.9-1.3 2.74-1.03 2.74-1.03.55 1.38.2 2.4.1 2.65.64.7 1.03 1.6 1.03 2.69 0 3.84-2.34 4.67-4.57 4.92.36.31.68.92.68 1.85v2.74c0 .27.18.59.69.49A10 10 0 0 0 12 2Z" />,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill={name === "github" ? "currentColor" : "none"} stroke={name === "github" ? "none" : "currentColor"} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
};

function ThemeButton({ theme, setTheme }) {
  return <button className="case-theme" type="button" onClick={() => setTheme(theme === "dark" ? "light" : "dark")} aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}><span aria-hidden="true">{theme === "dark" ? "☼" : "☾"}</span>{theme === "dark" ? "Light" : "Dark"}</button>;
}

function Gallery({ items, className = "" }) {
  const [selected, setSelected] = useState(null);
  useEffect(() => {
    const close = (event) => { if (event.key === "Escape") setSelected(null); };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, []);
  return <>
    <div className={`case-gallery ${className}`.trim()}>
      {items.map((item, index) => <button type="button" className={item.featured ? "is-featured" : ""} key={item.src} onClick={() => setSelected(index)} aria-label={`Open ${item.label}`}>
        <img src={asset(item.src)} alt={item.alt} loading={item.featured ? "eager" : "lazy"} />
        <span>{item.label}</span>
      </button>)}
    </div>
    {selected !== null && <div className="lightbox" role="dialog" aria-modal="true" aria-label={items[selected].label} onClick={() => setSelected(null)}><button type="button" autoFocus aria-label="Close image">×</button><img src={asset(items[selected].src)} alt={items[selected].alt} /></div>}
  </>;
}

function Architecture({ study }) {
  return <div className="architecture-flow architecture-detailed">{study.architecture.map(([title, detail, location], index) => <article key={title}><div><span>{String(index + 1).padStart(2, "0")}</span><em className={`runtime-badge ${location.startsWith("External") ? "is-external" : ""}`}>{location}</em></div><strong>{title}</strong><p>{detail}</p></article>)}</div>;
}

function ProductCaseStudy({ study, projectId }) {
  return <>
    <section className="case-section evidence-section" id="product-captures">
      <div className="evidence-heading"><div><span className="case-kicker">Real product captures</span><h2>{projectId === "meeting" ? "The working product, not a simulation." : "The learner journey, captured from the application."}</h2></div><p>{projectId === "meeting" ? "These screens come from the local Meeting Whisperer application and show capture, saved meetings, playback, transcript review, and speaker diarization." : "These are the actual dashboard, speaking, writing, evaluation, and administration interfaces from the IELTS application."}</p></div>
      <Gallery items={study.gallery} />
    </section>

    <section className="case-section flow-section">
      <div className="section-title"><span className="case-kicker">Product flow</span><h2>How the system is used.</h2></div>
      <div className="method-grid product-flow">{study.flow.map(([number, title, detail]) => <article key={number}><span>{number}</span><h3>{title}</h3><p>{detail}</p></article>)}</div>
    </section>

    <section className="case-section boundary-section" id="architecture">
      <div className="boundary-heading"><div><span className="case-kicker">Runtime architecture</span><h2>Speech stays local. Only the LLM crosses the boundary.</h2></div><div className="boundary-legend"><span><i /> Local or self-hosted</span><span><i className="external" /> External service</span></div></div>
      <Architecture study={study} />
      <div className="privacy-note"><strong>{projectId === "meeting" ? "Core transcription works without a cloud LLM." : "Audio is processed by local STT/TTS models."}</strong><p>{projectId === "meeting" ? "The optional external LLM is limited to text enrichment such as summaries or cleanup. Recordings and speech recognition remain under the deployment owner’s control." : "Only transcribed text needed for evaluation is sent to Gemini. faster-whisper and Kokoro run locally or on infrastructure you host."}</p></div>
    </section>
  </>;
}

function GraduationBody({ study }) {
  const pillars = [
    ["01", "Engineering foundations", "Learning to turn computer-science concepts into maintainable software, APIs, data models, and deployed systems."],
    ["02", "Applied AI", "Moving beyond model demos into speech, language, evaluation, and product workflows with clear system boundaries."],
    ["03", "Team delivery", "Planning work with collaborators, integrating independent components, and communicating technical decisions."],
    ["04", "Professional direction", "Using the degree as a launch point for production AI work in public services, health, education, and voice systems."],
  ];
  return <>
    <section className="case-section education-overview" id="education">
      <div><span className="case-kicker">Education</span><h2>A computer science degree grounded in building.</h2></div>
      <div className="education-card"><span>2022—2026</span><h3>BSc Computer Science</h3><p>Central Asian University · Engineering School<br />Tashkent, Uzbekistan</p><div><a href={study.university} target="_blank" rel="noreferrer">Official university <Icon name="arrow" size={16} /></a><a href={study.program} target="_blank" rel="noreferrer">Computer Science program <Icon name="arrow" size={16} /></a></div></div>
    </section>

    <section className="case-section"><div className="section-title"><span className="case-kicker">What the degree built</span><h2>Foundations that now show up in my work.</h2></div><div className="method-grid graduation-pillars">{pillars.map(([number, title, detail]) => <article key={number}><span>{number}</span><h3>{title}</h3><p>{detail}</p></article>)}</div></section>

    <section className="case-section transition-section"><div><span className="case-kicker">Beyond coursework</span><h2>From student projects to production responsibility.</h2></div><div className="transition-list"><p><strong>Build complete systems</strong><span>Frontend, backend, data, AI inference, deployment, and the operational details between them.</span></p><p><strong>Work on real constraints</strong><span>Multilingual users, realtime audio, privacy, human handoff, and services people depend on.</span></p><p><strong>Communicate the work</strong><span>Architecture, documentation, academic presentation, and collaboration across technical roles.</span></p></div></section>

    <section className="case-section poster-section graduation-poster" id="academic-poster"><div className="poster-copy"><span className="case-kicker">Original academic artifact</span><h2>The capstone poster.</h2><p>The final poster documents IELTS Mock Examiner as the degree’s capstone output. It is shown here in its original form; the dedicated IELTS case study documents the current implementation details.</p><div className="credit-list"><p><strong>Student team</strong>A. Khasakhojaev · J. Rustamov</p><p><strong>Supervisor</strong>Eugene Castro</p><p><strong>Institution</strong>Central Asian University · Engineering School</p></div><a className="case-secondary poster-project-link" href="../ielts-examiner/">View the current product case study <Icon name="arrow" size={16} /></a></div><Gallery items={study.gallery} className="poster-gallery" /></section>
  </>;
}

export default function CaseStudyApp({ projectId }) {
  const study = caseStudies[projectId] || caseStudies.meeting;
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || "dark");
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem("portfolio-theme", theme); document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "light" ? "#f4f0e8" : "#070511"); }, [theme]);
  const related = useMemo(() => Object.entries(caseStudies).filter(([key]) => key !== projectId), [projectId]);
  const sectionTarget = projectId === "graduation" ? "#education" : "#product-captures";

  return <div className={`case-site accent-${study.accent}`}>
    <header className="case-nav"><a href="../../" className="case-brand"><span>RJ</span>Javlonbek Rustamov</a><nav><a href="#story">Story</a><a href={sectionTarget}>{projectId === "graduation" ? "Education" : "Captures"}</a><ThemeButton theme={theme} setTheme={setTheme} /><a className="case-back" href={`../../#${projectId === "graduation" ? "graduation" : "work"}`}><Icon name="back" size={17} /> Portfolio</a></nav></header>
    <main>
      <section className="case-hero" id="story"><div className="case-hero-copy"><span className="case-kicker">{study.eyebrow}</span><h1>{study.title}</h1><p>{study.subtitle}</p><div className="case-actions"><a className="case-primary" href={sectionTarget}>{projectId === "graduation" ? "Explore the graduation story" : "View real captures"}<Icon name="arrow" size={18} /></a>{projectId !== "graduation" && <a className="case-secondary" href={study.repository} target="_blank" rel="noreferrer"><Icon name="github" size={18} /> Source code</a>}</div></div><div className="case-hero-art"><div className="case-orbit orbit-a" /><div className="case-orbit orbit-b" /><div className="case-emblem">{projectId === "meeting" ? "MW" : projectId === "ielts" ? "IE" : "26"}</div><span>{projectId === "graduation" ? "BSc Computer Science" : "Real application captures"}</span></div><div className="case-metrics">{study.metrics.map(([value, label]) => <div key={value}><strong>{value}</strong><span>{label}</span></div>)}</div></section>
      {projectId !== "graduation" ? <ProductCaseStudy study={study} projectId={projectId} /> : <GraduationBody study={study} />}
      {projectId === "ielts" && <section className="capstone-credit"><div><span className="case-kicker">Academic provenance</span><h2>Built as a collaborative BSc capstone.</h2><p>Central Asian University Engineering School · A. Khasakhojaev and J. Rustamov · supervised by Eugene Castro.</p></div><a className="case-secondary" href="../graduation-capstone/">Explore the graduation story <Icon name="arrow" size={17} /></a></section>}
      <section className="case-section toolkit-section"><div><span className="case-kicker">Working system</span><h2>{projectId === "graduation" ? "Knowledge carried into professional work." : "Technology chosen around the product."}</h2></div><div className="case-tags">{study.stack.map((item) => <span key={item}>{item}</span>)}</div></section>
      <section className="related-section"><span className="case-kicker">Continue exploring</span><div>{related.map(([key, item]) => <a href={`../${key === "meeting" ? "meeting-whisperer" : key === "ielts" ? "ielts-examiner" : "graduation-capstone"}/`} key={key}><span>{item.eyebrow}</span><strong>{item.title}</strong><Icon name="arrow" /></a>)}</div></section>
    </main>
    <footer className="case-footer"><a href="../../">RJ</a><p>Designed and built in Tashkent · Visual case study</p><a href="mailto:rjavlon2004@gmail.com">rjavlon2004@gmail.com</a></footer>
  </div>;
}
