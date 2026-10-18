import type { ThemeConfig } from 'antd'

/** SaaSGuard IAM 语义色：业务代码禁止直接新增十六进制颜色。 */
export const colors = {
  primary: '#3157d5',
  primaryStrong: '#2447b2',
  primarySoft: '#eef3ff',
  success: '#0f8f83',
  warning: '#b7791f',
  error: '#c43d4b',
  canvas: '#f5f7fb',
  surface: '#ffffff',
  text: '#172033',
  textSecondary: '#667085',
  textTertiary: '#5f6d83',
  border: '#dfe4ec',
  borderSecondary: '#e8ecf2',
} as const

/** Ant Design 全局主题。组件尺寸、圆角、颜色和状态只在此处定义。 */
export const authTheme: ThemeConfig = {
  token: {
    colorPrimary: colors.primary,
    colorInfo: colors.primary,
    colorLink: colors.primary,
    colorSuccess: colors.success,
    colorWarning: colors.warning,
    colorError: colors.error,
    colorBgLayout: colors.canvas,
    colorBgContainer: colors.surface,
    colorText: colors.text,
    colorTextSecondary: colors.textSecondary,
    colorBorder: colors.border,
    colorBorderSecondary: colors.borderSecondary,
    borderRadius: 8,
    borderRadiusLG: 12,
    fontSize: 14,
    controlHeight: 36,
    controlHeightLG: 46,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
  },
  components: {
    Button: {
      primaryShadow: '0 4px 12px rgba(49, 87, 213, 0.18)',
    },
    Card: {
      headerFontSize: 15,
      headerHeight: 52,
    },
    Input: {
      activeBorderColor: colors.primary,
      hoverBorderColor: '#9aabe8',
      activeShadow: '0 0 0 3px rgba(49, 87, 213, 0.10)',
    },
    Menu: {
      itemBg: colors.surface,
      itemColor: '#475467',
      itemHoverBg: '#f6f8fc',
      itemHoverColor: colors.text,
      itemSelectedBg: colors.primarySoft,
      itemSelectedColor: colors.primaryStrong,
      itemBorderRadius: 8,
      groupTitleColor: colors.textSecondary,
    },
    Table: {
      headerBg: '#f7f9fc',
      headerColor: '#475467',
      headerSplitColor: '#e4e7ec',
      rowHoverBg: '#f7faff',
      borderColor: colors.borderSecondary,
    },
    Modal: {
      titleFontSize: 17,
    },
  },
}
