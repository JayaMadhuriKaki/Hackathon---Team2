import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In dev, /api calls are proxied to the Spring Boot backend so the browser
// talks to one origin (no CORS). In Docker, nginx does the same job.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
