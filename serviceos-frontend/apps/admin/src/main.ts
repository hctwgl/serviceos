import { VueQueryPlugin } from '@tanstack/vue-query'
import { useVbenForm } from '@vben/common-ui'
import { initPreferences } from '@vben/preferences'
import { setupI18n } from '@vben/locales'
import { setupVbenVxeTable } from '@vben/plugins/vxe-table'
import { initStores, useAccessStore } from '@vben/stores'
import { createApp } from 'vue'
import '@vben/styles'
import '@vben/styles/antd'
import './design-system/tokens.css'
import './design-system/layout.css'
import './design-system/typography.css'
import './design-system/components.css'
import './styles/app.css'
import App from './App.vue'
import { serviceOsMenus } from './menus'
import { overridesPreferences } from './preferences'
import { router } from './router'

async function bootstrap() {
  const namespace = 'serviceos-frontend-vben-5-7-0'
  await initPreferences({ namespace, overrides: overridesPreferences })
  setupVbenVxeTable({
    configVxeTable: (vxeUI) => {
      vxeUI.setConfig({
        grid: {
          border: false,
          minHeight: 180,
          showOverflow: 'tooltip',
        },
      })
    },
    useVbenForm,
  })

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
