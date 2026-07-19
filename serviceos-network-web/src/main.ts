import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { completeLogin } from './auth/session'
import { safeProblemMessage } from '@serviceos/web-core'

let authReturnPath: string | null = null
try {
  if (window.location.pathname === '/auth/callback') {
    authReturnPath = await completeLogin(window.location.search)
  }
} catch (err) {
  console.error('[network-web:oidc]', safeProblemMessage(err), err)
  // 登录回调失败不得白屏：落到根路径让用户看到登录卡
  authReturnPath = '/'
}

const { router } = await import('./router')
if (authReturnPath) {
  await router.replace(authReturnPath)
}

const app = createApp(App)
app.config.errorHandler = (err, _instance, info) => {
  console.error('[network-web:errorHandler]', safeProblemMessage(err), info, err)
}
window.addEventListener('unhandledrejection', (event) => {
  console.error('[network-web:unhandledrejection]', safeProblemMessage(event.reason), event.reason)
})
app.use(router).mount('#app')
