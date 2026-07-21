import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import { SOS_TOKEN_VERSION } from './token-version'

/**
 * ServiceOS Ant Design Vue 主题映射（方案 A｜经典专业风：企业蓝主色）。
 * 语义成功/危险不得被品牌色替代。Token 版本：见 SOS_TOKEN_VERSION。
 */
export const serviceOsTheme: ThemeConfig = {
  token: {
    colorPrimary: '#1677FF',
    colorInfo: '#1677FF',
    colorSuccess: '#16A34A',
    colorWarning: '#D97706',
    colorError: '#DC2626',
    colorText: '#111827',
    colorTextSecondary: '#4B5563',
    colorTextTertiary: '#6B7280',
    colorBorder: '#E5E7EB',
    colorBorderSecondary: '#E5E7EB',
    colorBgLayout: '#F5F7FA',
    colorBgContainer: '#FFFFFF',
    colorBgElevated: '#FFFFFF',
    borderRadius: 6,
    borderRadiusLG: 8,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif",
    fontSize: 14,
    controlHeight: 32,
    controlHeightLG: 36,
    motionDurationFast: '0.12s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.32s',
  },
}

void SOS_TOKEN_VERSION
