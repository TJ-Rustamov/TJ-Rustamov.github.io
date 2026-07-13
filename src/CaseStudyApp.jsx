import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { caseStudies } from "./caseStudies.js";

const asset = (path) => `../../case-studies/${path}`;
const assetSet = (value) => value?.split(",").map((entry) => {
  const [path, width] = entry.trim().split(/\s+/);
  return `${asset(path)} ${width}`;
}).join(", ");

const Icon = ({ name, size = 20 }) => {
  const paths = {
    arrow: <path d="M7 17 17 7M8 7h9v9" />,
    back: <path d="m15 18-6-6 6-6" />,
    left: <path d="m15 18-6-6 6-6" />,
    right: <path d="m9 18 6-6-6-6" />,
    github: <path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.87c-2.78.6-3.37-1.18-3.37-1.18-.45-1.16-1.1-1.47-1.1-1.47-.91-.62.06-.61.06-.61 1 .07 1.53 1.03 1.53 1.03.89 1.53 2.34 1.09 2.9.83.09-.65.35-1.09.64-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.99 1.03-2.69-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.74 1.03A9.4 9.4 0 0 1 12 6.82a9.4 9.4 0 0 1 2.5.34c1.9-1.3 2.74-1.03 2.74-1.03.55 1.38.2 2.4.1 2.65.64.7 1.03 1.6 1.03 2.69 0 3.84-2.34 4.67-4.57 4.92.36.31.68.92.68 1.85v2.74c0 .27.18.59.69.49A10 10 0 0 0 12 2Z" />,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill={name === "github" ? "currentColor" : "none"} stroke={name === "github" ? "none" : "currentColor"} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
};

function ThemeButton({ theme, setTheme }) {
  return <button className="case-theme" type="button" onClick={() => setTheme(theme === "dark" ? "light" : "dark")} aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}><span className="case-theme-icon" aria-hidden="true">{theme === "dark" ? "☼" : "☾"}</span><span className="case-theme-label">{theme === "dark" ? "Light" : "Dark"}</span></button>;
}

function ResponsiveImage({ item, loading = "lazy", sizes = "100vw" }) {
  return <img src={asset(item.src)} srcSet={assetSet(item.srcSet)} sizes={item.srcSet ? sizes : undefined} alt={item.alt} loading={loading} draggable="false" style={item.position ? { objectPosition: item.position } : undefined} />;
}

function ImageDialog({ item, onClose }) {
  useEffect(() => {
    const close = (event) => { if (event.key === "Escape") onClose(); };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [onClose]);
  return <div className="lightbox" role="dialog" aria-modal="true" aria-label={item.label} onClick={onClose}><button type="button" autoFocus aria-label="Close image">×</button><div onClick={(event) => event.stopPropagation()}><ResponsiveImage item={item} loading="eager" /></div></div>;
}

function MediaCarousel({ slides, variant, ariaLabel, autoplayMs = 5500 }) {
  const rootRef = useRef(null);
  const trackRef = useRef(null);
  const scrollFrame = useRef(null);
  const [index, setIndex] = useState(0);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [visible, setVisible] = useState(false);
  const [tabVisible, setTabVisible] = useState(!document.hidden);
  const [reducedMotion, setReducedMotion] = useState(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  const [selected, setSelected] = useState(null);
  const paused = hovered || focused || !visible || !tabVisible || reducedMotion;

  const goTo = useCallback((nextIndex, behavior = "smooth") => {
    const normalized = (nextIndex + slides.length) % slides.length;
    const track = trackRef.current;
    if (track) track.scrollTo({ left: normalized * track.clientWidth, behavior: reducedMotion ? "auto" : behavior });
    setIndex(normalized);
  }, [reducedMotion, slides.length]);

  useEffect(() => {
    const observer = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting), { threshold: 0.35 });
    if (rootRef.current) observer.observe(rootRef.current);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReducedMotion(media.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    const update = () => setTabVisible(!document.hidden);
    document.addEventListener("visibilitychange", update);
    return () => document.removeEventListener("visibilitychange", update);
  }, []);

  useEffect(() => {
    if (paused || slides.length < 2) return undefined;
    const timer = window.setTimeout(() => goTo(index + 1), autoplayMs);
    return () => window.clearTimeout(timer);
  }, [autoplayMs, goTo, index, paused, slides.length]);

  useEffect(() => {
    const align = () => goTo(index, "auto");
    window.addEventListener("resize", align);
    return () => window.removeEventListener("resize", align);
  }, [goTo, index]);

  const onScroll = () => {
    if (scrollFrame.current) cancelAnimationFrame(scrollFrame.current);
    scrollFrame.current = requestAnimationFrame(() => {
      const track = trackRef.current;
      if (!track?.clientWidth) return;
      setIndex(Math.round(track.scrollLeft / track.clientWidth));
    });
  };

  const active = slides[index];
  return <>
    <div ref={rootRef} className={`media-carousel carousel-${variant}`} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocusCapture={() => setFocused(true)} onBlurCapture={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false); }}>
      <div className="carousel-visual">
        {variant === "ielts" && <div className="browser-chrome" aria-hidden="true"><i /><i /><i /><span>IELTS Mastery · learner workspace</span></div>}
        <div ref={trackRef} className="carousel-track" role="region" aria-roledescription="carousel" aria-label={ariaLabel} tabIndex="0" onScroll={onScroll} onKeyDown={(event) => { if (event.key === "ArrowLeft") { event.preventDefault(); goTo(index - 1); } if (event.key === "ArrowRight") { event.preventDefault(); goTo(index + 1); } }}>
          {slides.map((slide, slideIndex) => <button type="button" className="carousel-slide" key={slide.src} aria-label={`Open ${slide.title}`} aria-hidden={slideIndex !== index} tabIndex={slideIndex === index ? 0 : -1} onClick={() => setSelected(slide)}><ResponsiveImage item={slide} loading={slideIndex === 0 ? "eager" : "lazy"} sizes={variant === "graduation" ? "(max-width: 760px) 100vw, 58vw" : "(max-width: 980px) 100vw, 72vw"} /></button>)}
        </div>
      </div>

      <div className="carousel-meta" aria-live="polite">
        <div className="carousel-copy"><span>{active.label}</span><h3>{active.title}</h3><p>{active.description}</p></div>
        <div className="carousel-navigation">
          <strong>{String(index + 1).padStart(2, "0")} <i>/</i> {String(slides.length).padStart(2, "0")}</strong>
          <div className="carousel-buttons"><button type="button" onClick={() => goTo(index - 1)} aria-label="Previous slide"><Icon name="left" /></button><button type="button" onClick={() => goTo(index + 1)} aria-label="Next slide"><Icon name="right" /></button></div>
        </div>
        <div className={`carousel-progress ${paused ? "is-paused" : ""}`} key={`${index}-${paused}`} style={{ "--autoplay-duration": `${autoplayMs}ms` }}><i /></div>
        <div className="carousel-dots" aria-label="Choose slide">{slides.map((slide, slideIndex) => <button type="button" className={slideIndex === index ? "is-active" : ""} key={slide.src} onClick={() => goTo(slideIndex)} aria-label={`Go to slide ${slideIndex + 1}: ${slide.label}`} aria-current={slideIndex === index ? "true" : undefined} />)}</div>
      </div>
    </div>
    {selected && <ImageDialog item={selected} onClose={() => setSelected(null)} />}
  </>;
}

function Architecture({ study }) {
  return <div className="architecture-flow architecture-detailed">{study.architecture.map(([title, detail, location], index) => <article key={title}><div><span>{String(index + 1).padStart(2, "0")}</span><em className={`runtime-badge ${location.startsWith("External") ? "is-external" : ""}`}>{location}</em></div><strong>{title}</strong><p>{detail}</p></article>)}</div>;
}

function ProductCaseStudy({ study, projectId }) {
  const meeting = projectId === "meeting";
  return <>
    <section className={`case-section product-captures captures-${projectId}`} id="product-captures">
      <div className="evidence-heading"><div><span className="case-kicker">Real product captures</span><h2>{meeting ? "From a live conversation to a meeting you can return to." : "One practice journey, from question to focused feedback."}</h2></div><p>{meeting ? "Capture microphone and system audio, process speech locally, then review the original recording beside a speaker-aware transcript." : "The interface connects structured speaking and writing practice with saved attempts, criterion-level reports, and configurable administration."}</p></div>
      <MediaCarousel slides={study.slides} variant={projectId} ariaLabel={`${study.title} product screens`} />
    </section>

    <section className="case-section flow-section"><div className="section-title"><span className="case-kicker">Product flow</span><h2>How the system is used.</h2></div><div className="method-grid product-flow">{study.flow.map(([number, title, detail]) => <article key={number}><span>{number}</span><h3>{title}</h3><p>{detail}</p></article>)}</div></section>

    <section className="case-section boundary-section" id="architecture"><div className="boundary-heading"><div><span className="case-kicker">Runtime architecture</span><h2>Speech stays local. Only the LLM crosses the boundary.</h2></div><div className="boundary-legend"><span><i /> Local or self-hosted</span><span><i className="external" /> External service</span></div></div><Architecture study={study} /><div className="privacy-note"><strong>{meeting ? "Core transcription works without a cloud LLM." : "Audio is processed by local STT/TTS models."}</strong><p>{meeting ? "The optional external LLM is limited to text enrichment such as summaries or cleanup. Recordings and speech recognition remain under the deployment owner’s control." : "Only the text required for conversation and evaluation is sent to Gemini. faster-whisper and Kokoro run locally or on infrastructure you host."}</p></div></section>
  </>;
}

function GraduationHero({ study }) {
  return <section className="graduation-hero" id="story"><div className="graduation-hero-media"><ResponsiveImage item={study.heroImage} loading="eager" sizes="100vw" /></div><div className="graduation-hero-shade" /><div className="graduation-hero-copy"><span className="case-kicker">{study.eyebrow}</span><h1>{study.title}</h1><p>{study.subtitle}</p><a className="case-primary" href="#education">Read the story <Icon name="arrow" size={18} /></a></div><div className="graduation-hero-mark" aria-hidden="true"><span>Class of</span><strong>26</strong></div></section>;
}

function PosterViewer({ item }) {
  const [open, setOpen] = useState(false);
  return <><button className="poster-frame" type="button" onClick={() => setOpen(true)} aria-label="Open the academic capstone poster"><ResponsiveImage item={item} /><span>Open full poster <Icon name="arrow" size={16} /></span></button>{open && <ImageDialog item={item} onClose={() => setOpen(false)} />}</>;
}

function GraduationBody({ study }) {
  return <div className="graduation-story">
    <section className="graduation-facts" id="education"><div><span>Degree</span><strong>BSc Computer Science</strong></div><div><span>University</span><strong>Central Asian University</strong></div><div><span>School</span><strong>Engineering School</strong></div><div><span>Chapter</span><strong>2022—2026 · Tashkent</strong></div><div className="fact-links"><a href={study.university} target="_blank" rel="noreferrer">University <Icon name="arrow" size={15} /></a><a href={study.program} target="_blank" rel="noreferrer">CS program <Icon name="arrow" size={15} /></a></div></section>

    <section className="memory-story before-story"><div className="memory-copy"><span className="case-kicker">Before the gown</span><h2>The years before the stage.</h2><p>The photographs I return to are not only from graduation. They are the ordinary days when classmates became teammates, questions became projects, and progress was often something we made together.</p><blockquote>“The degree records what I studied. The people remind me how I grew.”</blockquote></div><MediaCarousel slides={study.beforeGraduation} variant="graduation" ariaLabel="University memories before graduation" autoplayMs={5500} /></section>

    <section className="stayed-section"><div className="stayed-intro"><span className="case-kicker">What stayed with me</span><h2>Lessons that outlast a classroom.</h2></div><div className="editorial-mosaic"><figure className="mosaic-photo mosaic-before"><ResponsiveImage item={study.reflectionPhotos[0]} sizes="(max-width: 760px) 100vw, 58vw" /><figcaption>Learning was rarely a solo act.</figcaption></figure><article className="mosaic-quote"><span>01 · Engineering foundations</span><p>Computer science taught me to break difficult problems into systems I could reason about, test, and improve.</p></article><article className="mosaic-quote mosaic-dark"><span>02 · Shared progress</span><p>The best work grew through conversation—asking better questions, dividing responsibility, and helping each other finish.</p></article><figure className="mosaic-photo mosaic-ceremony"><ResponsiveImage item={study.reflectionPhotos[1]} sizes="(max-width: 760px) 100vw, 42vw" /><figcaption>The ordinary days became part of the story.</figcaption></figure><article className="mosaic-wide"><span>03 · Professional direction</span><h3>Build the complete system, then care about what happens after it works.</h3><p>That mindset now shapes how I approach full-stack AI: understand the human need, own the connections between components, and make the result dependable enough for someone else to use.</p></article></div></section>

    <section className="memory-story ceremony-story"><MediaCarousel slides={study.ceremony} variant="graduation" ariaLabel="Central Asian University graduation ceremony" autoplayMs={5500} /><div className="memory-copy"><span className="case-kicker">The ceremony</span><h2>The day it became real.</h2><p>Graduation compressed years of work into one day—pride, gratitude, relief, and the realization that everyone standing there was about to choose a different next step.</p><blockquote>“For a moment it felt like an ending. Almost immediately, it felt like permission to begin again.”</blockquote><a className="graduation-social-link" href="https://www.linkedin.com/posts/cau-graduation2026-newleaders-ugcPost-7482453882040840193-i0PO/?utm_source=share&amp;utm_medium=member_desktop&amp;rcm=ACoAAE97SP0BUV4oYXP4VsFr8DxCyeYeucZo1eU" target="_blank" rel="noreferrer">View the CAU graduation post on LinkedIn <Icon name="arrow" size={16} /></a></div></section>

    <section className="graduation-poster" id="academic-poster"><div className="poster-copy"><span className="case-kicker">One academic output</span><h2>The capstone poster.</h2><p>IELTS Mock Examiner brought the degree’s technical and collaborative lessons into one final system. The original poster remains here as an artifact of that work; the product case study documents the current implementation.</p><div className="credit-list"><p><strong>Student team</strong>A. Khasakhojaev · J. Rustamov</p><p><strong>Supervisor</strong>Eugene Castro</p><p><strong>Institution</strong>Central Asian University · Engineering School</p></div><a className="case-secondary poster-project-link" href="../ielts-examiner/">View the current product case study <Icon name="arrow" size={16} /></a></div><PosterViewer item={study.poster} /></section>

    <section className="graduation-closing"><span className="case-kicker">Carried forward</span><h2>Not a finish line.</h2><p>I left CAU with stronger technical foundations, but also with a clearer idea of the engineer I want to become: someone who can understand a real problem, build the complete system around it, and make the result useful to other people.</p><div><a className="case-primary" href="../../#experience">View experience <Icon name="arrow" size={17} /></a><a className="case-secondary" href="../../#work">Explore selected work <Icon name="arrow" size={17} /></a></div></section>
  </div>;
}

export default function CaseStudyApp({ projectId }) {
  const study = caseStudies[projectId] || caseStudies.meeting;
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || "dark");
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem("portfolio-theme", theme); document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "light" ? "#f4f0e8" : "#070511"); }, [theme]);
  const related = useMemo(() => Object.entries(caseStudies).filter(([key]) => key !== projectId), [projectId]);
  const graduation = projectId === "graduation";
  const sectionTarget = graduation ? "#education" : "#product-captures";

  return <div className={`case-site case-${projectId} accent-${study.accent}`}>
    <header className="case-nav"><a href="../../" className="case-brand"><span>RJ</span>Javlonbek Rustamov</a><nav><a href="#story">Story</a><a href={sectionTarget}>{graduation ? "Memories" : "Captures"}</a><ThemeButton theme={theme} setTheme={setTheme} /><a className="case-back" href={`../../#${graduation ? "graduation" : "work"}`}><Icon name="back" size={17} /><span className="case-back-label">Portfolio</span></a></nav></header>
    <main>
      {graduation ? <GraduationHero study={study} /> : <section className="case-hero" id="story"><div className="case-hero-copy"><span className="case-kicker">{study.eyebrow}</span><h1>{study.title}</h1><p>{study.subtitle}</p><div className="case-actions"><a className="case-primary" href={sectionTarget}>View real captures <Icon name="arrow" size={18} /></a><a className="case-secondary" href={study.repository} target="_blank" rel="noreferrer"><Icon name="github" size={18} /> Source code</a></div></div><div className="case-hero-art"><div className="case-orbit orbit-a" /><div className="case-orbit orbit-b" /><div className="case-emblem">{projectId === "meeting" ? "MW" : "IE"}</div><span>Real application captures</span></div><div className="case-metrics">{study.metrics.map(([value, label]) => <div key={value}><strong>{value}</strong><span>{label}</span></div>)}</div></section>}
      {graduation ? <GraduationBody study={study} /> : <ProductCaseStudy study={study} projectId={projectId} />}
      {!graduation && <>{projectId === "ielts" && <section className="capstone-credit"><div><span className="case-kicker">Academic provenance</span><h2>Built as a collaborative BSc capstone.</h2><p>Central Asian University Engineering School · A. Khasakhojaev and J. Rustamov · supervised by Eugene Castro.</p></div><a className="case-secondary" href="../graduation-capstone/">Explore the graduation story <Icon name="arrow" size={17} /></a></section>}<section className="case-section toolkit-section"><div><span className="case-kicker">Working system</span><h2>Technology chosen around the product.</h2></div><div className="case-tags">{study.stack.map((item) => <span key={item}>{item}</span>)}</div></section><section className="related-section"><span className="case-kicker">Continue exploring</span><div>{related.map(([key, item]) => <a href={`../${key === "meeting" ? "meeting-whisperer" : key === "ielts" ? "ielts-examiner" : "graduation-capstone"}/`} key={key}><span>{item.eyebrow}</span><strong>{item.title}</strong><Icon name="arrow" /></a>)}</div></section></>}
    </main>
    <footer className="case-footer"><a href="../../">RJ</a><p>Designed and built in Tashkent · {graduation ? "A personal graduation story" : "Visual product case study"}</p><a href="mailto:rjavlon2004@gmail.com">rjavlon2004@gmail.com</a></footer>
  </div>;
}
