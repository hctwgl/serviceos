import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 师傅端为独立移动优先应用（不使用 Vben 桌面壳），但位于同一 serviceos-frontend 工作区，
// 复用 @serviceos/auth-context 等共享包。经 Vite /api 同源代理访问后端，避免受限文件上传的 CORS。
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5175,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_PROXY ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
