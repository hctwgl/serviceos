import { createApp } from 'vue'
import 'ant-design-vue/dist/reset.css'
import './styles/global.css'
import App from './App.vue'
import { router } from './router'
import { toUserFacingError } from './product/errorMessages'

const app = createApp(App)

app.config.errorHandler = (err, _instance, info) => {
  const facing = toUserFacingError(err)
  console.error('[admin-web:errorHandler]', facing.errorCode, info, err)
}

window.addEventListener('unhandledrejection', (event) => {
  const facing = toUserFacingError(event.reason)
  console.error('[admin-web:unhandledrejection]', facing.errorCode, event.reason)
})

app.use(router).mount('#app')
