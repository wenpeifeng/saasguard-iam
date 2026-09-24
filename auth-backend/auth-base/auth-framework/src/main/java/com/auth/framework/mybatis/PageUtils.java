package com.auth.framework.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.auth.common.result.PageResult;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Function;

/**
 * 分页查询工具 —— 统一 MP 分页对象与 {@link PageResult} 的转换，
 * 把 Controller/Service 里重复的分页样板代码收口到一处；
 * 另提供排序白名单转换（{@link PageQuery} 的 sorts → ORDER BY）。
 *
 * <pre>{@code
 * PageResult<UserVO> page = PageUtils.of(
 *         userMapper, Wrappers.lambdaQuery(SysUser.class).like(SysUser::getName, name),
 *         pageNum, pageSize, UserVO::from);
 * }</pre>
 */
@Slf4j
public final class PageUtils {

    /** 属性名合法字符（白名单之外的输入直接忽略，防 SQL 注入） */
    private static final Pattern SAFE_FIELD = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    /** 实体属性名缓存（Class → 蛇形列名集合），避免逐请求反射 */
    private static final ConcurrentHashMap<Class<?>, Set<String>> FIELD_CACHE = new ConcurrentHashMap<>();

    private PageUtils() {
    }

    /**
     * 执行分页查询并转换为 VO 分页结果。
     *
     * @param baseMapper 模块 Mapper（MP 自动生成，天然具备 selectPage）
     * @param wrapper    查询条件（null 表示无条件）
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页条数
     * @param converter  实体→VO 转换函数（如 UserVO::from）
     */
    public static <E, V> PageResult<V> of(
            BaseMapper<E> baseMapper,
            LambdaQueryWrapper<E> wrapper,
            long pageNum, long pageSize,
            Function<E, V> converter) {
        IPage<E> page = baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(converter).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 分页查询实体本身（无需 VO 转换的场景）。
     */
    public static <E> PageResult<E> of(
            BaseMapper<E> baseMapper,
            LambdaQueryWrapper<E> wrapper,
            long pageNum, long pageSize) {
        return of(baseMapper, wrapper, pageNum, pageSize, e -> e);
    }

    /**
     * 分页 + 排序一步完成：先按白名单应用 sorts，再执行分页查询。
     *
     * @param baseMapper  模块 Mapper
     * @param wrapper     查询条件
     * @param query       分页查询对象（pageNum/pageSize/sorts）
     * @param entityClass 实体类型（排序字段白名单来源）
     * @param converter   实体→VO 转换函数
     */
    public static <E, V> PageResult<V> of(
            BaseMapper<E> baseMapper,
            LambdaQueryWrapper<E> wrapper,
            PageQuery query,
            Class<E> entityClass,
            Function<E, V> converter) {
        applySorts(wrapper, query == null ? null : query.getSorts(), entityClass);
        long pageNum = query == null ? 1 : query.getPageNum();
        long pageSize = query == null ? 10 : query.getPageSize();
        return of(baseMapper, wrapper, pageNum, pageSize, converter);
    }

    /**
     * 应用排序（包装到 wrapper.last，分页插件的 LIMIT 会拼在其后）。
     * <p>
     * 防注入规则：字段必须命中实体真实属性名（含父类 BaseEntity 的审计字段），
     * 驼峰/下划线写法均可；蛇形列名只由框架按属性名生成，用户串不直接进 SQL。
     * 非法字段直接忽略并 log.warn（不抛错，保证列表可用性）。
     *
     * @param wrapper     MP 查询条件
     * @param sorts       排序字段列表（可空）
     * @param entityClass 实体类型（白名单来源）
     */
    public static <E> void applySorts(LambdaQueryWrapper<E> wrapper,
                                      List<PageQuery.SortField> sorts,
                                      Class<?> entityClass) {
        String orderBy = buildOrderBy(sorts, entityClass);
        if (!orderBy.isEmpty()) {
            wrapper.last(orderBy);
        }
    }

    /**
     * 构造 ORDER BY 子句（不含 "ORDER BY" 关键字时返回空串）。
     *
     * @param sorts       排序字段列表
     * @param entityClass 实体类型（白名单）
     * @return 如 "ORDER BY order_date DESC, id ASC"；全部非法/为空返回 ""
     */
    public static String buildOrderBy(List<PageQuery.SortField> sorts, Class<?> entityClass) {
        if (sorts == null || sorts.isEmpty()) {
            return "";
        }
        Set<String> allowed = FIELD_CACHE.computeIfAbsent(entityClass, PageUtils::collectFieldNames);
        List<String> clauses = new ArrayList<>();
        for (PageQuery.SortField sort : sorts) {
            if (sort == null || sort.getField() == null || sort.getField().isBlank()) {
                continue;
            }
            String raw = sort.getField().trim();
            if (!SAFE_FIELD.matcher(raw).matches()) {
                log.warn("排序字段[{}]含非法字符，已忽略（实体{}）", raw, entityClass.getSimpleName());
                continue;
            }
            // 归一化：下划线写法 → 驼峰属性名；再统一蛇形化得到列名
            String property = snakeToCamel(raw);
            if (!allowed.contains(property)) {
                log.warn("排序字段[{}]不在实体{}白名单内，已忽略", raw, entityClass.getSimpleName());
                continue;
            }
            String column = camelToSnake(property);
            boolean desc = sort.getDirection() != null
                    && "desc".equalsIgnoreCase(sort.getDirection().trim());
            if (sort.getDirection() != null && !desc && !"asc".equalsIgnoreCase(sort.getDirection().trim())) {
                log.warn("排序方向[{}]非法（仅 asc/desc），字段{}按 asc 处理", sort.getDirection(), raw);
            }
            clauses.add(column + (desc ? " DESC" : " ASC"));
        }
        return clauses.isEmpty() ? "" : "ORDER BY " + String.join(", ", clauses);
    }

    /** 收集实体全部属性名（含父类；跳过 this$ 等合成字段），统一驼峰小写起首 */
    private static Set<String> collectFieldNames(Class<?> entityClass) {
        return Stream.<Class<?>>iterate(entityClass, c -> c != Object.class, Class::getSuperclass)
                .flatMap(c -> Stream.of(c.getDeclaredFields()))
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 下划线 → 驼峰（order_date → orderDate） */
    private static String snakeToCamel(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean upper = false;
        for (char ch : s.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
                upper = false;
            }
        }
        return sb.toString();
    }

    /** 驼峰 → 蛇形（orderDate → order_date） */
    private static String camelToSnake(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char ch : s.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                sb.append('_').append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
