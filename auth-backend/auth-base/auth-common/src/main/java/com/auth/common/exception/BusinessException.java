package com.auth.common.exception;

import com.auth.common.result.ErrorCode;
import lombok.Getter;

/**
 * 业务异常 —— 所有可预期的业务校验失败都抛本异常，
 * 由全局异常处理器（erp-framework 的 GlobalExceptionHandler）统一转为 Result 响应。
 * <p>
 * 国际化（i18n）两种用法：
 * <ol>
 *   <li><b>消息 key（推荐，新代码）</b>：{@code throw BusinessException.ofKey(MessageKeys.BIZ_STOCK_NOT_ENOUGH,
 *       warehouse, material, qty)} —— 处理器按请求 Accept-Language 解析中英文；
 *       {@code ErrorCode} 枚举构造（如 {@code new BusinessException(ErrorCode.DATA_NOT_EXISTS)}）自动携带 codeKey。</li>
 *   <li><b>字面文案（存量，逐步迁移）</b>：{@code throw new BusinessException("仓库[%s]不存在", warehouseName)}
 *       —— 中文原样输出，不参与本地化。</li>
 * </ol>
 * erp-common 不依赖 Spring，本类只携带 key 与参数；消息解析在 erp-framework 的 AuthMessages 完成。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，默认 1000（业务处理失败） */
    private final int code;
    /** i18n 消息 key（auth.biz.* / auth.error.*，见 MessageKeys）；字面文案构造时为 null */
    private final String messageKey;
    /** 消息占位参数（MessageFormat {0}{1}...），与 messageKey 配套 */
    private final transient Object[] messageArgs;

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.BUSINESS_ERROR.getCode();
        this.messageKey = null;
        this.messageArgs = null;
    }

    /** 支持 %s 占位符格式化，免手动 String.format */
    public BusinessException(String pattern, Object... args) {
        super(pattern.formatted(args));
        this.code = ErrorCode.BUSINESS_ERROR.getCode();
        this.messageKey = null;
        this.messageArgs = null;
    }

    /** 按错误码枚举构造：message 取枚举默认文案，messageKey 取枚举 codeKey（响应时按 Locale 本地化） */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.messageKey = errorCode.getCodeKey();
        this.messageArgs = null;
    }

    /**
     * 按错误码枚举 + 自定义字面文案构造（存量用法，文案原样输出不走 i18n）。
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.messageKey = null;
        this.messageArgs = null;
    }

    public BusinessException(Throwable cause, ErrorCode errorCode) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.messageKey = errorCode.getCodeKey();
        this.messageArgs = null;
    }

    /**
     * 按错误码枚举 + 消息 key 构造（响应时按请求 Locale 解析，缺失时回退枚举默认文案）。
     * <p>
     * 注意：两参调用 {@code new BusinessException(ec, "文案")} 仍匹配
     * {@link #BusinessException(ErrorCode, String)}（字面文案），带 key 需显式传占位参数或改用
     * {@link #ofKey(ErrorCode, String, Object...)}。
     *
     * @param errorCode   业务错误码
     * @param messageKey  i18n 消息 key（建议用 MessageKeys 常量）
     * @param messageArgs 占位参数，无参时传 {@code new Object[0]} 或改用工厂方法
     */
    public BusinessException(ErrorCode errorCode, String messageKey, Object... messageArgs) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    /** 按消息 key 构造（业务码默认 1000）：{@code ofKey(MessageKeys.BIZ_QTY_POSITIVE)} */
    public static BusinessException ofKey(String messageKey, Object... messageArgs) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, messageKey, messageArgs);
    }

    /** 按错误码枚举 + 消息 key 构造：{@code ofKey(ErrorCode.DATA_NOT_EXISTS, MessageKeys.BIZ_DOC_NOT_EXISTS, docNo)} */
    public static BusinessException ofKey(ErrorCode errorCode, String messageKey, Object... messageArgs) {
        return new BusinessException(errorCode, messageKey, messageArgs);
    }
}
