import { defineConfig } from "vite";
export default defineConfig({
  server: { proxy: { "/api": "http://localhost:8080" } },
  build: { outDir: "../backend/src/main/resources/static", emptyOutDir: true },
});
