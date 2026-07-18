import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { completeLogin } from './auth/session'

const authReturnPath = window.location.pathname === '/auth/callback'
  ? await completeLogin(window.location.search)
  : null
const { router } = await import('./router')
// 用 Router 自己完成回调地址替换，确保其内部 currentRoute 与浏览器地址保持一致。
if (authReturnPath) await router.replace(authReturnPath)
createApp(App).use(router).mount('#app')
