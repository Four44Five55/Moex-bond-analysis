import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Порты 5173/4173 заняты — используем 5180 (dev) и 4180 (preview).
// /api проксируется на Spring Boot backend (порт 8090).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5180,
    proxy: {
      "/api": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 4180,
  },
});
