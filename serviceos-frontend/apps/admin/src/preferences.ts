import { defineOverridesPreferences } from '@vben/preferences'

/**
 * ServiceOS 固定使用 A+ 浅色企业布局。这里直接锁定 Vben 运行时偏好，不向业务用户开放主题、
 * 暗色、布局切换或页面密度偏好，避免同一产品出现多套无法验收的视觉结果。
 */
export const overridesPreferences = defineOverridesPreferences({
  app: {
    compact: false,
    contentCompact: 'wide',
    contentPadding: 0,
    defaultHomePath: '/workbench',
    dynamicTitle: true,
    enableCheckUpdates: false,
    enableCopyPreferences: false,
    enablePreferences: false,
    layout: 'sidebar-nav',
    locale: 'zh-CN',
    name: 'ServiceOS',
  },
  breadcrumb: { enable: false },
  copyright: { enable: false },
  footer: { enable: false },
  header: { enable: true, height: 54, mode: 'fixed' },
  logo: { enable: true, fit: 'contain', source: '/favicon.svg' },
  navigation: { accordion: true, split: false, styleType: 'rounded' },
  shortcutKeys: {
    enable: false,
    globalLockScreen: false,
    globalLogout: false,
    globalPreferences: false,
    globalSearch: false,
  },
  sidebar: {
    collapsed: false,
    collapsedButton: true,
    collapsedShowTitle: false,
    collapseWidth: 60,
    draggable: false,
    enable: true,
    expandOnHover: false,
    fixedButton: false,
    hidden: false,
    width: 176,
  },
  tabbar: { enable: false, persist: false },
  theme: {
    builtinType: 'default',
    colorPrimary: 'hsl(217 100% 55%)',
    fontSize: 14,
    mode: 'light',
    radius: '0.4375',
    semiDarkHeader: false,
    semiDarkSidebar: false,
    semiDarkSidebarSub: false,
  },
  transition: { enable: false, loading: false, progress: false },
  widget: {
    fullscreen: false,
    globalSearch: false,
    languageToggle: false,
    lockScreen: false,
    notification: false,
    refresh: false,
    sidebarToggle: false,
    themeToggle: false,
    timezone: false,
  },
})
