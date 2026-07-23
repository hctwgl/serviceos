import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { completeLogin } from './auth/session'
import { safeProblemMessage } from './api/client'

// OIDC 回调只在本入口处理一次：交换令牌后把返回路径收敛到师傅端；令牌由 @serviceos/auth-context 落地。
let authReturnPath: string | null = null
try {
  if (window.location.pathname === '/auth/callback') {
    const returnTo = await completeLogin(window.location.search)
    authReturnPath = returnTo.startsWith('/technician-portal') ? returnTo : '/technician-portal/task-feed'
  }
} catch (err) {
  console.error('[technician-web:oidc]', safeProblemMessage(err), err)
  authReturnPath = '/technician-portal/task-feed'
}

const { router } = await import('./router')
if (authReturnPath) {
  await router.replace(authReturnPath)
}

const app = createApp(App)
app.config.errorHandler = (err, _instance, info) => {
  console.error('[technician-web:errorHandler]', safeProblemMessage(err), info, err)
}
window.addEventListener('unhandledrejection', (event) => {
  console.error('[technician-web:unhandledrejection]', safeProblemMessage(event.reason), event.reason)
})
app.use(router).mount('#app')
