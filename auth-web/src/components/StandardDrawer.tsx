import { Drawer } from 'antd'
import type { DrawerProps } from 'antd'

export type DrawerSize = 'medium' | 'large'

const DRAWER_WIDTH: Record<DrawerSize, string> = {
  medium: 'min(92vw, 35rem)',
  large: 'min(94vw, 60rem)',
}

interface StandardDrawerProps extends Omit<DrawerProps, 'width' | 'size'> {
  size?: DrawerSize
}

/** 标准侧边任务面板：统一宽度和数据保护，正文区域自动独立滚动。 */
export default function StandardDrawer({
  size = 'medium',
  maskClosable = false,
  destroyOnClose = true,
  ...props
}: StandardDrawerProps) {
  return (
    <Drawer
      {...props}
      width={DRAWER_WIDTH[size]}
      maskClosable={maskClosable}
      destroyOnClose={destroyOnClose}
    />
  )
}
