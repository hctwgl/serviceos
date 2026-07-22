import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

export { Button, ConfigProvider, Drawer, Empty, Input, Radio, Select } from 'ant-design-vue'
export {
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CalendarOutlined,
  CheckOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  CopyOutlined,
  DownOutlined,
  DownloadOutlined,
  FileDoneOutlined,
  HomeOutlined,
  MenuFoldOutlined,
  QuestionCircleOutlined,
  RightOutlined,
  SafetyCertificateFilled,
  SearchOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'

export const serviceOsZhCN = zhCN

export const serviceOsTheme: ThemeConfig = {
  token: {
    colorPrimary: '#1769ff',
    colorInfo: '#1769ff',
    colorSuccess: '#20b26b',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    colorText: '#172033',
    colorTextSecondary: '#667085',
    colorBorder: '#e7ebf2',
    colorBorderSecondary: '#edf0f5',
    colorBgLayout: '#f5f7fb',
    colorBgContainer: '#ffffff',
    borderRadius: 5,
    borderRadiusLG: 8,
    controlHeight: 34,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif",
    fontSize: 14,
  },
}
