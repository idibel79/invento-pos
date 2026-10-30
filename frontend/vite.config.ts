/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// En développement, l'API Spring Boot tourne sur le port 8080 : même origine via le proxy,
// donc le cookie de rafraîchissement (SameSite=Strict, chemin /api/v1/auth) fonctionne.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
