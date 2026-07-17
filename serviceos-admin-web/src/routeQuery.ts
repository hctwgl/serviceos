import type { RouteLocationNormalizedLoaded, RouteLocationRaw } from 'vue-router'

/** 读取路由 query 的首个字符串值；缺省返回 undefined（保留页面默认筛选）。 */
export function firstRouteQuery(
  route: RouteLocationNormalizedLoaded,
  name: string,
): string | undefined {
  const raw = route.query[name]
  if (typeof raw === 'string') {
    return raw
  }
  if (Array.isArray(raw) && typeof raw[0] === 'string') {
    return raw[0]
  }
  return undefined
}

/** 将非空 UUID 字符串解析为命名路由；空值保持明文（不发明深链）。 */
export function uuidRoute(value: unknown, name: string): RouteLocationRaw | null {
  return typeof value === 'string' && value
    ? { name, params: { id: value } }
    : null
}
