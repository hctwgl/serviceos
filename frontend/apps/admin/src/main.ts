import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createApp } from 'vue'
import '@serviceos/design-system/reset.css'
import '@serviceos/design-system/tokens.css'
import './styles/app.css'
import App from './App.vue'
import { router } from './router'

createApp(App).use(createPinia()).use(VueQueryPlugin).use(router).mount('#app')
