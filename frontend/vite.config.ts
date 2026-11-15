import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

const localOnlyMode = process.env.VITE_E2E === 'true' || process.env.VITE_ANT_DEMO === 'true'

if (localOnlyMode) {
  const target = new URL(apiProxyTarget)
  const loopbackHosts = new Set(['127.0.0.1', 'localhost', '[::1]'])
  if (
    target.protocol !== 'http:'
    || !loopbackHosts.has(target.hostname)
    || target.username !== ''
    || target.password !== ''
    || target.pathname !== '/'
    || target.search !== ''
    || target.hash !== ''
  ) {
    throw new Error('E2E and ANT demo modes only allow the Vite API proxy to target a local HTTP backend.')
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    ...(process.env.VITE_ANT_DEMO === 'true' ? { host: '127.0.0.1' } : {}),
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
})
