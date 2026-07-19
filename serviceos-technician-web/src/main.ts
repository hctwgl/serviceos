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
  console.error('[technician-web:oidc]', safeProblemMessage(err), err)
  authReturnPath = '/'
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
