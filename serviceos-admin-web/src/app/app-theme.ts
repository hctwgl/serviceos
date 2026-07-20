import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import { SOS_TOKEN_VERSION } from './token-version'

/**
 * ServiceOS Ant Design Vue 主题映射（深海蓝主色 + 新能源青强调）。
 * 语义成功/危险不得被品牌色替代。Token 版本：见 SOS_TOKEN_VERSION。
 */
export const serviceOsTheme: ThemeConfig = {
  token: {
    colorPrimary: '#1E5B84',
    colorInfo: '#3578C6',
    colorSuccess: '#2F9461',
    colorWarning: '#C97B13',
    colorError: '#D14343',
    colorText: '#1F2937',
    colorTextSecondary: '#4B5563',
    colorTextTertiary: '#7B8494',
    colorBorder: '#DFE3E8',
    colorBorderSecondary: '#EAEDF0',
    colorBgLayout: '#F4F6F8',
    colorBgContainer: '#FFFFFF',
    colorBgElevated: '#FFFFFF',
    borderRadius: 4,
    borderRadiusLG: 8,
    fontFamily:
      "Inter, 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', Arial, sans-serif",
    fontSize: 14,
    controlHeight: 32,
    controlHeightLG: 36,
    motionDurationFast: '0.12s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.32s',
  },
}

void SOS_TOKEN_VERSION
