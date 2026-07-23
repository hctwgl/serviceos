import { defineConfig } from '@vben/vite-config'

export default defineConfig(async () => ({
  application: {},
  vite: {
    resolve: {
      alias: {
        '@': new URL('./src', import.meta.url).pathname,
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      allowedHosts: ['localhost', 'terminal.local'],
      proxy: {
        '/api': 'http://localhost:8080',
      },
    },
  },
}))
