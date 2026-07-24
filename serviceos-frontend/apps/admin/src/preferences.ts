import { defineOverridesPreferences } from '@vben/preferences'

/**
 * ServiceOS 使用 Vben Admin 5 的原生浅色主题和侧栏布局。这里只关闭产品当前不提供的个性化
 * 入口，不再重定义品牌色、圆角和组件密度，避免形成一套与 Vben 冲突的第二主题。
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
  header: { enable: true, height: 50, mode: 'fixed' },
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
    // 产品导航保持稳定展开，避免鼠标离开后退化成难以识别的图标栏；用户仍可手动收起。
    expandOnHover: true,
    fixedButton: false,
    hidden: false,
    width: 224,
  },
  tabbar: { enable: false, persist: false },
  theme: {
    builtinType: 'default',
    mode: 'light',
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
    sidebarToggle: true,
    themeToggle: false,
    timezone: false,
  },
})
