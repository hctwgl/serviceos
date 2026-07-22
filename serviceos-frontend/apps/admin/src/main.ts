import { VueQueryPlugin } from '@tanstack/vue-query'
import { initPreferences } from '@vben/preferences'
import { setupI18n } from '@vben/locales'
import { initStores, useAccessStore } from '@vben/stores'
import { createApp } from 'vue'
import '@vben/styles'
import '@vben/styles/antd'
import '@serviceos/design-system/tokens.css'
import './styles/app.css'
import App from './App.vue'
import { serviceOsMenus } from './menus'
import { overridesPreferences } from './preferences'
import { router } from './router'

async function bootstrap() {
  const namespace = 'serviceos-admin-vben-5-7-0'
  await initPreferences({ namespace, overrides: overridesPreferences })

  const app = createApp(App)
  await setupI18n(app, { defaultLocale: 'zh-CN' })
  await initStores(app, { namespace })

  const accessStore = useAccessStore()
  accessStore.setAccessMenus(serviceOsMenus)
  accessStore.setIsAccessChecked(true)

  app.use(VueQueryPlugin)
  app.use(router)
  app.mount('#app')
}

void bootstrap()
