import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev proxy: the React dev server (9200) forwards /api and /ws to control-service (9002),
// so the browser sees a single origin and there is no CORS to configure. In production the
// built assets are served by control-service itself (same origin), so no proxy is needed.
export default defineConfig({
  plugins: [react()],
  define: { global: 'globalThis' }, // sockjs-client references `global`
  server: {
    port: 9200,
    proxy: {
      '/api': 'http://localhost:9002',
      '/ws': { target: 'http://localhost:9002', ws: true },
    },
  },
})
