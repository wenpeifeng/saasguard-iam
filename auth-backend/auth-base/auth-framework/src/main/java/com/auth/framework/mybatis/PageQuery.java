package com.auth.framework.mybatis;

import lombok.Data;

import java.util.List;

/**
 * 分页查询基类 —— 各模块 Query 对象继承后统一获得分页 + 多字段排序能力。
 * <p>
 * 排序经 {@link PageUtils#applySorts} 转换为白名单校验后的 ORDER BY
 * （白名单 = 实体真实属性名，蛇形列名仅由框架生成，用户输入不直接拼接 SQL）。
 * <pre>{@code
 * public class OrderQuery extends PageQuery {
 *     private String keyword;
 * }
 * PageUtils.applySorts(wrapper, query.getSorts(), PurOrder.class);
 * PageUtils.of(mapper, wrapper, query.getPageNum(), query.getPageSize(), VO::from);
 * }</pre>
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private long pageNum = 1;

    /** 每页条数（分页插件单页上限 500） */
    private long pageSize = 10;

    /** 排序字段列表（按声明顺序生效，非法字段被忽略并告警） */
    private List<SortField> sorts;

    /**
     * 排序字段：field 为实体属性名（支持驼峰或下划线写法），direction 为 asc/desc。
     */
    @Data
    public static class SortField {

        /** 实体属性名（如 orderDate / order_date） */
        private String field;

        /** 方向：asc / desc（大小写不敏感，缺省 asc） */
        private String direction;
    }
}
