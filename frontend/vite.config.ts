import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The dev server proxies /api to the Spring Boot backend, so the browser
// only talks to one origin (no CORS setup needed in development).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  // Homework 2: unit/component tests (Vitest + React Testing Library).
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'text-summary', 'html', 'json-summary'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**'],
      reportsDirectory: './coverage',
    },
  },
})
