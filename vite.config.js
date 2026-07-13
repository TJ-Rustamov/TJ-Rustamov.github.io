import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "node:path";

export default defineConfig({
  plugins: [react()],
  base: "./",
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, "index.html"),
        meetingWhisperer: resolve(__dirname, "projects/meeting-whisperer/index.html"),
        ieltsExaminer: resolve(__dirname, "projects/ielts-examiner/index.html"),
        graduationCapstone: resolve(__dirname, "projects/graduation-capstone/index.html"),
      },
    },
  },
});
