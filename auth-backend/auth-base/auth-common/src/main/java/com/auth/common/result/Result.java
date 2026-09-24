package com.auth.common.result;

import lombok.Getter;

/**
 * 统一响应体 —— 整个系统所有 REST 接口的返回格式。
 * <p>
 * 结构：{@code {"code": 0, "message": "操作成功", "messageKey": null, "data": {...}}}。
 * 前端约定：code == 0 视为成功，非 0 走错误分支。
 * <p>
 * 国际化（i18n）：{@link #message} 由服务端按请求 Accept-Language 解析后的最终文案
 * （可直接展示）；{@link #messageKey} 为消息 key（auth.error.* / auth.biz.*，成功或字面
 * 文案时为 null），前端 react-i18next 可据此做本地化兜底（见 docs/modules/i18n.md）。
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> {

    /** 业务状态码，0 表示成功 */
    private final int code;
    /** 提示信息（按请求 Locale 本地化后的最终文案，可直接展示） */
    private final String message;
    /** i18n 消息 key（错误响应携带，前端可本地化兜底；成功/字面文案为 null） */
    private final String messageKey;
    /** 业务数据，失败时通常为 null */
    private final T data;
    /** 服务端时间戳（毫秒），便于排查问题时对时 */
    private final long timestamp;

    private Result(int code, String message, String messageKey, T data) {
        this.code = code;
        this.message = message;
        this.messageKey = messageKey;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /** 成功（带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null, data);
    }

    /** 成功（无数据） */
    public static Result<Void> ok() {
        return ok(null);
    }

    /** 失败（指定错误码，文案走枚举默认中文——需本地化时由框架层调 {@link #fail(int, String, String)}） */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    /** 失败（指定错误码 + 覆盖文案） */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null, null);
    }

    /** 失败（自定义业务码，供各模块细分场景使用） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    /** 失败（自定义业务码 + 已本地化文案 + 消息 key）—— i18n 统一出口，供框架异常处理器使用 */
    public static <T> Result<T> fail(int code, String message, String messageKey) {
        return new Result<>(code, message, messageKey, null);
    }
}
