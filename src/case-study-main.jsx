import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import AmbientNetwork from "./AmbientNetwork.jsx";
import CaseStudyApp from "./CaseStudyApp.jsx";
import "./styles.css";
import "./case-studies.css";

const project = document.body.dataset.project || "meeting";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AmbientNetwork />
    <CaseStudyApp projectId={project} />
  </StrictMode>,
);
