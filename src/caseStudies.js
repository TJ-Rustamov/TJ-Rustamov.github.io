export const caseStudies = {
  meeting: {
    eyebrow: "Local-first voice intelligence",
    title: "Meeting Whisperer",
    subtitle: "A private, real-time meeting recorder that turns microphone and system audio into speaker-aware transcripts and searchable meeting history.",
    accent: "pink",
    repository: "https://github.com/TJ-Rustamov/Meeting-whisperer",
    stack: ["React", "FastAPI", "WebSockets", "faster-whisper", "Silero VAD", "pyannote", "SQLite", "Docker"],
    metrics: [["2 sources", "Microphone + system audio"], ["Local AI", "Speech stays on the machine"], ["Realtime", "Partial and final transcript events"]],
    architecture: [
      ["Capture", "MediaRecorder + AudioWorklet"],
      ["Stream", "FastAPI WebSocket"],
      ["Understand", "VAD + faster-whisper"],
      ["Organize", "Diarization + SQLite"],
    ],
    gallery: [
      { src: "meeting/overview.webp", alt: "Meeting Whisperer recorder with source and audio controls", label: "Recorder workspace" },
    ],
  },
  ielts: {
    eyebrow: "AI-assisted language learning",
    title: "IELTS Mock Examiner",
    subtitle: "A full-stack practice platform combining natural speaking sessions, structured writing feedback, progress tracking, and configurable question banks.",
    accent: "teal",
    repository: "https://github.com/TJ-Rustamov/ielts_mock_examiner",
    stack: ["React", "Django", "Channels", "Gemini", "faster-whisper", "Kokoro TTS", "SQLite", "Docker"],
    metrics: [["2 modules", "Speaking + writing"], ["3 AI layers", "LLM + STT + TTS"], ["Full-stack", "Student and admin workflows"]],
    architecture: [
      ["Experience", "React + Vite"],
      ["Realtime", "Django Channels"],
      ["Speech", "Whisper + Kokoro"],
      ["Feedback", "Gemini + SQLite"],
    ],
    gallery: [
      { src: "ielts/dashboard.webp", alt: "IELTS Mock Examiner learner dashboard", label: "Learner dashboard" },
      { src: "ielts/speaking-process.webp", alt: "IELTS speaking test interface", label: "Speaking session" },
      { src: "ielts/speaking-evaluation.webp", alt: "IELTS speaking evaluation report", label: "Speaking feedback" },
      { src: "ielts/writing-process.webp", alt: "IELTS writing task interface", label: "Writing task" },
      { src: "ielts/writing-evaluation.webp", alt: "IELTS writing evaluation report", label: "Writing feedback" },
      { src: "ielts/admin.webp", alt: "IELTS Mock Examiner administration interface", label: "Admin controls" },
    ],
  },
  graduation: {
    eyebrow: "BSc Computer Science · 2026",
    title: "Graduation Capstone",
    subtitle: "The academic story behind IELTS Mock Examiner: framing an access problem, designing a real-time AI system, and delivering an integrated writing and speaking platform.",
    accent: "orange",
    repository: "https://github.com/TJ-Rustamov/ielts_mock_examiner",
    stack: ["Applied AI", "Full-stack engineering", "Realtime audio", "UX research", "Docker", "Technical communication"],
    metrics: [["Central Asian University", "Engineering School"], ["Team project", "A. Khasakhojaev + J. Rustamov"], ["Supervisor", "Eugene Castro"]],
    architecture: [
      ["Need", "Accessible repeated practice"],
      ["Method", "Client-server + realtime audio"],
      ["System", "Writing and speaking pipelines"],
      ["Outcome", "Integrated practice experience"],
    ],
    gallery: [
      { src: "ielts/dashboard.webp", alt: "Graduation project dashboard output", label: "Dashboard output" },
      { src: "ielts/speaking-process.webp", alt: "Graduation project speaking workflow", label: "Speaking workflow" },
      { src: "ielts/writing-process.webp", alt: "Graduation project writing workflow", label: "Writing workflow" },
      { src: "ielts/admin.webp", alt: "Graduation project administration interface", label: "Admin system" },
    ],
  },
};

export const meetingTranscript = [
  { speaker: "Maya", role: "Product lead", text: "Let us align on the launch. The accessibility review is complete, but the transcript export still needs validation.", audio: "meeting/01-zira.wav" },
  { speaker: "Daniel", role: "Engineer", text: "I can own the export check today. The remaining question is whether speaker labels persist after editing.", audio: "meeting/02-david.wav" },
  { speaker: "Maya", role: "Product lead", text: "Good. Let us test that with a two speaker recording and document the expected behavior.", audio: "meeting/03-zira.wav" },
  { speaker: "Daniel", role: "Engineer", text: "Agreed. I will share the results before four o clock and flag any diarization errors.", audio: "meeting/04-david.wav" },
];

export const speakingStages = [
  { label: "Welcome", prompt: "Welcome to your IELTS speaking practice session.", audio: "ielts/greeting.wav", time: 15 },
  { label: "Part 1", prompt: "Let us begin with your hometown. What do you like most about the place where you live?", audio: "ielts/greeting_part1.wav", time: 30 },
  { label: "Part 2", prompt: "Describe a useful skill you learned from another person. You have one minute to prepare.", audio: "ielts/prep_instructions.wav", time: 60 },
  { label: "Complete", prompt: "Your shortened demonstration is complete. Here is an illustrative feedback report.", audio: "ielts/farewell.wav", time: 0 },
];
