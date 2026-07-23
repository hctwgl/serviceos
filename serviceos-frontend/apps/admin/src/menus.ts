import type { MenuRecordRaw } from '@vben/types'

export const serviceOsMenus: MenuRecordRaw[] = [
  { name: '工作台', path: '/workbench', icon: 'lucide:house', order: 10 },
  {
    name: '工单运营',
    path: '/work-orders',
    icon: 'lucide:clipboard-list',
    order: 20,
    children: [
      { name: '我的工单', path: '/work-orders?view=mine' },
      { name: '工单池', path: '/work-orders' },
      { name: '工单看板', path: '/work-orders?view=board' },
      { name: '工单查询', path: '/work-orders?view=all' },
    ],
  },
  { name: '服务履约', path: '/work-orders?view=fulfillment', icon: 'lucide:list-checks', order: 30 },
  { name: '审核与整改', path: '/work-orders?view=review', icon: 'lucide:clipboard-check', order: 40 },
  {
    name: '客户与项目',
    path: '/projects',
    icon: 'lucide:briefcase-business',
    order: 50,
    children: [
      { name: '项目管理', path: '/projects' },
      { name: '客户品牌', path: '/clients' },
    ],
  },
  {
    name: '组织与资源',
    path: '/resources',
    icon: 'lucide:users-round',
    order: 60,
    children: [
      { name: '组织架构', path: '/resources/organizations' },
      { name: '服务网点', path: '/resources/networks' },
      { name: '师傅档案', path: '/resources/technicians' },
    ],
  },
  {
    name: '系统管理',
    path: '/system',
    icon: 'lucide:settings',
    order: 70,
    children: [
      { name: '用户管理', path: '/system/users' },
      { name: '角色与授权', path: '/system/roles' },
    ],
  },
  { name: '审计与监控', path: '/work-orders?view=audit', icon: 'lucide:chart-no-axes-combined', order: 80 },
]
