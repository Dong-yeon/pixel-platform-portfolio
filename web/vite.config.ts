import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev proxy: the React dev server (5173) forwards /api and /ws to control-service (8082),
// so the browser sees a single origin and there is no CORS to configure. In production the
// built assets are served by control-service itself (same origin), so no proxy is needed.
export default defineConfig({
  plugins: [react()],
  define: { global: 'globalThis' }, // sockjs-client references `global`
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8082',
      '/ws': { target: 'http://localhost:8082', ws: true },
    },
  },
})
