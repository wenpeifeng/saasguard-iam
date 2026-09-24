package com.auth.common.result;

import com.auth.common.i18n.MessageKeys;
import lombok.Getter;

/**
 * 全局错误码枚举。
 * <p>
 * 编码规则（5 位）：
 * <ul>
 *   <li>00000 - 成功</li>
 *   <li>Axxxx - 通用客户端错误（参数/认证/授权等）</li>
 *   <li>Bxxxx - 系统服务端错误</li>
 *   <li>1xxxx~9xxxx - 预留给各业务模块（见各模块 xxxErrorCode）</li>
 * </ul>
 * <p>
 * 国际化（i18n）：每个枚举对应一个消息 key（{@link #codeKey}，命名 {@code auth.error.*}，
 * 常量见 com.auth.common.i18n.MessageKeys），由 erp-framework 的 AuthMessages
 * 按请求 Accept-Language 解析中英文；{@link #message} 为中文兜底文案
 * （消息文件缺失该 key 时直接使用，保证永不返回空白）。
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "操作成功", MessageKeys.SUCCESS),

    /** 通用客户端错误 */
    BAD_REQUEST(400, "请求参数错误", MessageKeys.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或登录已过期", MessageKeys.UNAUTHORIZED),
    FORBIDDEN(403, "没有访问权限", MessageKeys.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", MessageKeys.NOT_FOUND),
    METHOD_NOT_ALLOWED(405, "请求方式不支持", MessageKeys.METHOD_NOT_ALLOWED),

    /** 通用服务端错误 */
    INTERNAL_ERROR(500, "系统内部异常", MessageKeys.INTERNAL_ERROR),
    SERVICE_UNAVAILABLE(503, "服务暂不可用", MessageKeys.SERVICE_UNAVAILABLE),

    /** 业务通用 */
    BUSINESS_ERROR(1000, "业务处理失败", MessageKeys.BUSINESS_ERROR),
    DUPLICATE_DATA(1001, "数据已存在，请勿重复提交", MessageKeys.DUPLICATE_DATA),
    DATA_NOT_EXISTS(1002, "数据不存在或已被删除", MessageKeys.DATA_NOT_EXISTS),
    DATA_IN_USE(1003, "数据被引用，无法删除", MessageKeys.DATA_IN_USE);

    /** 错误码 */
    private final int code;
    /** 错误提示（可直接展示给用户；同时作为 i18n 缺失 key 时的中文兜底） */
    private final String message;
    /** i18n 消息 key（auth.error.*，见 MessageKeys / i18n/messages*.properties） */
    private final String codeKey;

    ErrorCode(int code, String message, String codeKey) {
        this.code = code;
        this.message = message;
        this.codeKey = codeKey;
    }
}
