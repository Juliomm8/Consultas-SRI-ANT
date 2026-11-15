import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

if (process.env.VITE_E2E === 'true') {
  const target = new URL(apiProxyTarget)
  const loopbackHosts = new Set(['127.0.0.1', 'localhost', '[::1]'])
  if (target.protocol !== 'http:' || !loopbackHosts.has(target.hostname)) {
    throw new Error('E2E mode only allows the Vite API proxy to target a local HTTP backend.')
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
