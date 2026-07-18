import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { completeDevelopmentLogin } from './auth/session'
import { router } from './router'

if (window.location.pathname === '/auth/callback') {
  await completeDevelopmentLogin(window.location.search)
  window.history.replaceState({}, '', '/')
}
createApp(App).use(router).mount('#app')
