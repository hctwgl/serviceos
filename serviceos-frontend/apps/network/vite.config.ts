import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 网点协作端为桌面型独立 Vite 应用，位于同一 serviceos-frontend 工作区，复用 @serviceos/auth-context 等共享包。
// 经 Vite /api 同源代理访问后端；受限文件上传/下载也走同源避免 CORS。
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_PROXY ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
