import { Modal } from 'antd'
import type { ModalProps } from 'antd'

export type ModalSize = 'small' | 'medium' | 'large' | 'wide'

const MODAL_WIDTH: Record<ModalSize, string> = {
  small: 'min(92vw, 30rem)',
  medium: 'min(92vw, 40rem)',
  large: 'min(94vw, 60rem)',
  wide: 'min(96vw, 70rem)',
}

interface StandardModalProps extends Omit<ModalProps, 'width'> {
  /** 统一流体宽度等级；随视口缩放并受最大宽度约束。 */
  size?: ModalSize
}

/**
 * 标准业务弹框：统一宽度、数据保护和销毁行为；高度与滚动由全局样式控制。
 */
export default function StandardModal({
  size = 'medium',
  maskClosable = false,
  destroyOnClose = true,
  ...props
}: StandardModalProps) {
  return (
    <Modal
      {...props}
      width={MODAL_WIDTH[size]}
      maskClosable={maskClosable}
      destroyOnClose={destroyOnClose}
    />
  )
}
