import { Typography } from 'antd'

const { Title } = Typography

interface PageHeaderProps {
  /** 页面标题（如"采购订单"） */
  title: string
  /** 可选说明文案 */
  subtitle?: string
  /** 右侧操作区（如"新建"按钮） */
  extra?: React.ReactNode
}

/**
 * 统一页头 —— 全系统内容页顶栏的标准样式。
 * <p>
 * 布局：左标题+说明，右操作按钮区。所有列表/单据页必须用它，
 * 保证全站标题层级与间距一致（商业系统统一规范，禁止各页面自写 h 标题）。
 */
export default function PageHeader({ title, subtitle, extra }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div className="page-header-copy">
        <Title level={3} className="page-title">
          {title}
        </Title>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {extra && <div className="page-actions">{extra}</div>}
    </div>
  )
}
