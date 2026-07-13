import { useEffect, useState } from "react";
import AmbientNetwork from "./AmbientNetwork.jsx";

const ArrowUpRight = ({ size = 18 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path d="M7 17 17 7M8 7h9v9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const Github = () => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M12 .7a11.5 11.5 0 0 0-3.64 22.41c.58.11.79-.25.79-.56v-2.23c-3.22.7-3.9-1.37-3.9-1.37-.52-1.34-1.28-1.7-1.28-1.7-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.57-.3-5.27-1.29-5.27-5.7 0-1.27.45-2.3 1.19-3.11-.12-.29-.52-1.47.11-3.07 0 0 .97-.31 3.17 1.19a10.9 10.9 0 0 1 5.78 0c2.2-1.5 3.17-1.19 3.17-1.19.63 1.6.23 2.78.11 3.07.74.81 1.19 1.84 1.19 3.11 0 4.43-2.71 5.4-5.29 5.69.42.36.79 1.06.79 2.14v3.17c0 .31.21.68.8.56A11.5 11.5 0 0 0 12 .7Z" />
  </svg>
);

const Linkedin = () => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M5.34 7.53H1.7V22h3.64V7.53ZM3.52 1.8a2.12 2.12 0 1 0 0 4.24 2.12 2.12 0 0 0 0-4.24ZM22.3 13.7c0-4.36-2.33-6.39-5.44-6.39-2.5 0-3.63 1.38-4.25 2.35V7.53H8.97V22h3.64v-7.16c0-1.89.36-3.72 2.7-3.72 2.31 0 2.34 2.16 2.34 3.84V22h3.64l.01-8.3Z" />
  </svg>
);

const Gitlab = () => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="m12 22.25 4.42-13.6H7.58L12 22.25Zm0 0L7.58 8.65H1.4L12 22.25ZM1.4 8.65.05 12.78c-.12.38.01.8.33 1.03L12 22.25 1.4 8.65Zm0 0h6.18L4.93.5c-.14-.42-.73-.42-.87 0L1.4 8.65Zm10.6 13.6 4.42-13.6h6.18L12 22.25Zm10.6-13.6 1.35 4.13c.12.38-.01.8-.33 1.03L12 22.25 22.6 8.65Zm0 0h-6.18L19.07.5c.14-.42.73-.42.87 0l2.66 8.15Z" />
  </svg>
);

const VoiceWaveform = () => {
  const bars = [8, 15, 24, 34, 46, 28, 18, 38, 52, 33, 21, 42, 56, 35, 24, 16, 9];
  return (
    <span className="voice-wave" aria-hidden="true">
      <span className="voice-pulse" />
      {bars.map((height, index) => (
        <i key={`${height}-${index}`} style={{ "--wave-height": `${height}%`, "--wave-delay": `${index * -0.075}s` }} />
      ))}
    </span>
  );
};

const ThemeIcon = ({ theme }) => (
  <svg className="theme-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    {theme === "dark" ? (
      <>
        <circle cx="12" cy="12" r="3.5" stroke="currentColor" strokeWidth="1.7" />
        <path d="M12 2.5v2M12 19.5v2M21.5 12h-2M4.5 12h-2M18.72 5.28l-1.42 1.42M6.7 17.3l-1.42 1.42M18.72 18.72l-1.42-1.42M6.7 6.7 5.28 5.28" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </>
    ) : (
      <path d="M20.35 15.15A8.5 8.5 0 0 1 8.85 3.65 8.5 8.5 0 1 0 20.35 15.15Z" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    )}
  </svg>
);

const projects = [
  {
    number: "01",
    domain: "Civic technology · Government services",
    title: "Youth Affairs Voice Assistant",
    summary:
      "A production voice AI system that helps citizens navigate youth programs, submit structured appeals, and reach a human operator when needed.",
    impact: [
      "Built real-time inbound calling with Python, LiveKit, SIP, and Asterisk",
      "Designed guided flows for public benefits and citizen-service requests",
      "Added reliable human handoff, transcript persistence, and outcome tracking",
      "Improved Uzbek speech quality with STT/TTS normalization and tuning",
    ],
    stack: ["Python", "LiveKit", "SIP", "RAG", "Qdrant", "Docker"],
    tone: "orange",
    link: "https://navai.pro/",
    linkLabel: "NavAI",
  },
  {
    number: "02",
    domain: "Public health · Healthcare access",
    title: "SinoAI Health Platform",
    summary:
      "A multilingual healthcare product and operations platform built to make AI-assisted health information accessible across Uzbekistan.",
    impact: [
      "Developed the FastAPI admin backend, data model, authentication, and RBAC",
      "Designed and shipped the public React website in four language variants",
      "Integrated dynamic health content, partner workflows, and technical SEO",
      "Owned production delivery with GitLab CI/CD, Docker, and Nginx",
    ],
    stack: ["FastAPI", "React", "PostgreSQL", "i18next", "Docker", "Nginx"],
    tone: "green",
    link: "https://sinoai.io/",
    linkLabel: "Visit sinoai.io",
  },
  {
    number: "03",
    domain: "Education · Language learning",
    title: "AI Speaking Assessment",
    summary:
      "An AI-powered learning experience that turns spoken English into clear feedback for learners working to improve communication skills.",
    impact: [
      "Integrated Whisper and GPT-4o for transcription and feedback",
      "Implemented voice activity detection for precise speech segmentation",
      "Connected Django and React into a unified product experience",
      "Helped deliver two AI learning platforms to production in one month",
    ],
    stack: ["Django", "React", "Whisper", "GPT-4o", "VAD"],
    tone: "blue",
    link: "https://improvely.ai/",
    linkLabel: "Visit improvely.ai",
  },
  {
    number: "04",
    domain: "Workforce development · Integrations",
    title: "Recruitment Workflow Platform",
    summary:
      "A multi-tenant backend that connects employers with external job boards and simplifies vacancy publishing across services.",
    impact: [
      "Built API services with FastAPI, SQLAlchemy, PostgreSQL, and Celery",
      "Integrated recruiting platforms including rabota.ru and ZipRecruiter",
      "Implemented Google and LinkedIn OAuth authentication",
      "Collaborated with a distributed team and documented delivery progress",
    ],
    stack: ["FastAPI", "PostgreSQL", "Celery", "OAuth", "Redis"],
    tone: "purple",
  },
];

const projectGroups = [
  {
    label: "Official work experience",
    description: "Production systems delivered as part of my formal roles at NavAI and SinoAI.",
    projects: projects.slice(0, 2),
  },
  {
    label: "Other selected projects",
    description: "Additional products and platform work across education and recruitment.",
    projects: projects.slice(2),
  },
];

const experience = [
  ["2026 — now", "SinoAI", "Fullstack Developer & AI/ML Engineer"],
  ["2025 — 2026", "NavAI", "Fullstack Developer & AI Engineer"],
  ["2025", "Ibrat Academy", "Fullstack Developer & Voice AI Engineer"],
  ["2025", "AVBINVEST", "Python Developer"],
];

const principles = [
  ["01", "Understand the real need", "Start with the people doing the work, the constraints they face, and the outcome that matters."],
  ["02", "Ship the smallest useful system", "Turn ambiguity into a working tool, then improve it using real operational feedback."],
  ["03", "Design for human ownership", "Use clear flows, documentation, observability, and reliable handoffs so the system survives beyond the demo."],
  ["04", "Measure and improve", "Track outcomes, catch failure modes, and treat production behavior as the source of truth."],
];

function App() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || "dark");

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem("portfolio-theme", theme);
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "light" ? "#f4f0e8" : "#070511");
  }, [theme]);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((entry) => entry.target.classList.toggle("is-visible", entry.isIntersecting)),
      { threshold: 0.1, rootMargin: "-6% 0px -6% 0px" },
    );
    document.querySelectorAll("[data-motion]").forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!window.location.hash) return;
    const target = document.querySelector(window.location.hash);
    if (!target) return;
    requestAnimationFrame(() => target.scrollIntoView({ block: "start" }));
  }, []);

  const copyEmail = async () => {
    await navigator.clipboard.writeText("rjavlon2004@gmail.com");
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  };

  return (
    <div className="site-shell">
      <AmbientNetwork />
      <header className="nav-wrap">
        <a className="brand" href="#top" aria-label="Javlonbek Rustamov, home">
          <span className="brand-mark">RJ</span>
          <span>Javlonbek Rustamov</span>
        </a>
        <button
          className="menu-button"
          type="button"
          aria-label="Toggle navigation"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((value) => !value)}
        >
          <span />
          <span />
        </button>
        <nav className={menuOpen ? "nav-links is-open" : "nav-links"} aria-label="Main navigation">
          <a href="#work" onClick={() => setMenuOpen(false)}>Work</a>
          <a href="#about" onClick={() => setMenuOpen(false)}>About</a>
          <a href="#experience" onClick={() => setMenuOpen(false)}>Experience</a>
          <button
            className="theme-toggle"
            type="button"
            aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
            aria-pressed={theme === "light"}
            onClick={() => setTheme((current) => current === "dark" ? "light" : "dark")}
          >
            <ThemeIcon theme={theme} />
            <span>{theme === "dark" ? "Light" : "Dark"}</span>
          </button>
          <a className="nav-cta" href="#contact" onClick={() => setMenuOpen(false)}>Let’s talk <ArrowUpRight size={16} /></a>
        </nav>
      </header>

      <main id="top">
        <section className="hero section-pad">
          <div className="hero-copy" data-motion="left">
            <div className="eyebrow"><span className="status-dot" /> Tashkent, Uzbekistan · Building for real-world impact</div>
            <h1>I build AI systems that make complex services <em>clear and usable.</em></h1>
            <p className="hero-lede">
              I’m Javlonbek, a fullstack AI engineer working across voice agents, multilingual platforms, and production infrastructure—especially for government, healthcare, and education.
            </p>
            <div className="hero-actions">
              <a className="button button-dark" href="#work">Explore selected work <ArrowUpRight /></a>
              <a className="text-link" href="https://github.com/TJ-Rustamov" target="_blank" rel="noreferrer">View GitHub <ArrowUpRight size={16} /></a>
            </div>
          </div>
          <div className="hero-visual" data-motion="right">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="signal-card signal-main">
              <span className="signal-label">CURRENT FOCUS</span>
              <strong>AI for public impact</strong>
              <p>From a citizen’s question to a system that can respond, act, and hand off safely.</p>
            </div>
            <div className="signal-card signal-small signal-top"><span className="voice-label">Voice AI</span><VoiceWaveform /></div>
            <div className="signal-card signal-small signal-bottom">4 languages <span>●</span></div>
            <div className="hero-monogram">RJ</div>
          </div>
          <div className="hero-proof" data-motion="up">
            <div><strong>Production</strong><span>Voice & LLM systems</span></div>
            <div><strong>4 domains</strong><span>Government to health</span></div>
            <div><strong>End-to-end</strong><span>Scope, build, deploy</span></div>
            <div><strong>3 languages</strong><span>Uzbek, English, Russian</span></div>
          </div>
        </section>

        <section className="marquee" aria-label="Core technologies">
          <div className="marquee-track">
            {["Python", "FastAPI", "React", "LiveKit", "RAG", "PostgreSQL", "Docker", "Voice AI", "Python", "FastAPI", "React", "LiveKit", "RAG", "PostgreSQL", "Docker", "Voice AI"].map((item, index) => (
              <span key={`${item}-${index}`}>{item}<i>✦</i></span>
            ))}
          </div>
        </section>

        <section className="work section-pad" id="work">
          <div className="section-heading" data-motion="left">
            <div><span className="kicker">Selected work</span><h2>Systems built around people, not demos.</h2></div>
            <p>Each project began with a practical constraint and ended as something people could actually use.</p>
          </div>
          <div className="project-groups">
            {projectGroups.map((group, groupIndex) => (
              <section className="project-group" key={group.label} aria-labelledby={`project-group-${groupIndex}`}>
                <div className="project-group-heading" data-motion={groupIndex % 2 === 0 ? "left" : "right"}>
                  <h3 id={`project-group-${groupIndex}`}>{group.label}</h3>
                  <p>{group.description}</p>
                </div>
                <div className="project-list">
                  {group.projects.map((project, index) => (
                    <article className={`project-card tone-${project.tone}`} key={project.number} data-motion={(index + groupIndex) % 2 === 0 ? "left" : "right"}>
                      <div className="project-index">{project.number}</div>
                      <div className="project-main">
                        <span className="project-domain">{project.domain}</span>
                        <h3>{project.title}</h3>
                        <p className="project-summary">{project.summary}</p>
                        <ul>
                          {project.impact.map((point) => <li key={point}>{point}</li>)}
                        </ul>
                        <div className="tag-row">{project.stack.map((tag) => <span key={tag}>{tag}</span>)}</div>
                      </div>
                      <div className="project-side">
                        <div className="project-glyph" aria-hidden="true"><span>{project.number}</span></div>
                        {project.link && <a href={project.link} target="_blank" rel="noreferrer">{project.linkLabel} <ArrowUpRight size={17} /></a>}
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            ))}
          </div>
        </section>

        <section className="about section-pad" id="about">
          <div className="about-grid">
            <div className="about-title" data-motion="left">
              <span className="kicker">How I work</span>
              <h2>A utility player for AI delivery.</h2>
            </div>
            <div className="about-copy" data-motion="right">
              <p className="lead">I’m most useful where technology meets a messy real-world process.</p>
              <p>I can move from stakeholder needs and conversation design to APIs, interfaces, deployment, monitoring, and handoff. My goal is not simply to make an AI feature work—it is to make the whole system understandable, dependable, and useful.</p>
            </div>
          </div>
          <div className="principles-grid">
            {principles.map(([number, title, copy], index) => (
              <article key={number} data-motion={index % 2 === 0 ? "left" : "right"} style={{ "--motion-delay": `${index * 70}ms` }}><span>{number}</span><h3>{title}</h3><p>{copy}</p></article>
            ))}
          </div>
        </section>

        <section className="experience section-pad" id="experience">
          <div className="section-heading compact" data-motion="left">
            <div><span className="kicker">Experience</span><h2>Building across the stack.</h2></div>
            <p>BSc Computer Science, Central Asian University · 2026</p>
          </div>
          <div className="experience-list">
            {experience.map(([date, company, role], index) => (
              <div className="experience-row" key={company} data-motion={index % 2 === 0 ? "left" : "right"} style={{ "--motion-delay": `${index * 55}ms` }}>
                <span>{date}</span><strong>{company}</strong><p>{role}</p>
              </div>
            ))}
          </div>
          <div className="skills-block" data-motion="up">
            <span className="kicker">Working toolkit</span>
            <div className="skills-cloud">
              {[
                "Python", "FastAPI", "Django", "React", "JavaScript", "SQL", "PostgreSQL", "MongoDB", "Redis", "Qdrant",
                "LiveKit", "Asterisk", "SIP", "STT / TTS", "RAG", "OpenAI", "Gemini", "Docker", "Nginx", "GitLab CI/CD", "Prometheus",
              ].map((skill, index) => <span key={skill} style={{ "--skill-delay": `${(index % 8) * 45}ms` }}>{skill}</span>)}
            </div>
          </div>
        </section>

        <section className="contact section-pad" id="contact">
          <div className="contact-card" data-motion="scale">
            <span className="kicker light">Let’s build something useful</span>
            <h2>Have a hard problem where AI could make a real difference?</h2>
            <p>I’m interested in mission-driven AI work, production agent systems, and teams that care about what happens after the demo.</p>
            <div className="contact-actions">
              <a
                className="button button-light"
                href="https://mail.google.com/mail/?view=cm&fs=1&to=rjavlon2004%40gmail.com&su=Portfolio%20inquiry&body=Hi%20Javlonbek%2C%0A%0AI%20found%20your%20portfolio%20and%20would%20like%20to%20connect.%0A"
                target="_blank"
                rel="noreferrer"
              >Email me <ArrowUpRight /></a>
              <button className="copy-button" type="button" onClick={copyEmail}>{copied ? "Copied!" : "Copy email"}</button>
            </div>
          </div>
        </section>
      </main>

      <footer>
        <div><strong>Javlonbek Rustamov</strong><span>AI Engineer & Fullstack Developer</span></div>
        <p>Designed and built with care in Tashkent.</p>
        <div className="social-links">
          <a href="https://github.com/TJ-Rustamov" target="_blank" rel="noreferrer" aria-label="GitHub"><Github /></a>
          <a href="https://www.linkedin.com/in/javlonbek-rustamov-7656b5312/" target="_blank" rel="noreferrer" aria-label="LinkedIn"><Linkedin /></a>
          <a href="https://gitlab.sinoai.io/Rustamov" target="_blank" rel="noreferrer" aria-label="GitLab"><Gitlab /></a>
        </div>
      </footer>
    </div>
  );
}

export default App;
