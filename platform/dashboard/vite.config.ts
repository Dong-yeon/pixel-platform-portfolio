import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 대시보드는 게이트웨이(9000)만 바라본다 — 모듈(9001/9002)에 직접 붙지 않는다.
// dev 서버가 /api·/ws를 게이트웨이로 프록시하므로 브라우저에는 단일 오리진으로 보인다.
export default defineConfig({
  plugins: [react()],
  define: { global: 'globalThis' }, // sockjs-client가 `global`을 참조한다
  server: {
    port: 9100,
    proxy: {
      '/api': 'http://localhost:9000',
      '/ws': { target: 'http://localhost:9000', ws: true },
    },
  },
})
