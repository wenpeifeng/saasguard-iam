package com.auth.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应体。
 * <p>
 * 与 MyBatis-Plus 的 IPage 解耦，Controller 层负责将 IPage 转为本对象，
 * 前端列表页统一消费 {@code records / total / pageNum / pageSize} 四个字段。
 *
 * @param <T> 记录类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /**
     * 通用构造：从 MP 的分页结果转换。
     *
     * @param records  当前页数据
     * @param total    总数
     * @param pageNum  页码
     * @param pageSize 每页条数
     */
    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    /**
     * 转换元素类型：常用于 Entity 分页结果直接映射为 VO 分页结果。
     *
     * @param mapper 实体到 VO 的转换函数（如 UserVO::from）
     */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(
                records.stream().map(mapper).toList(),
                total, pageNum, pageSize);
    }
}
