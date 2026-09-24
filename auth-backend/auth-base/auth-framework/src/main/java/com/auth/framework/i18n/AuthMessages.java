package com.auth.framework.i18n;

import com.auth.common.result.ErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Locale;

/**
 * 消息解析静态门面 —— 按"当前请求 Locale"取 i18n 文案，供异常体系
 * （GlobalExceptionHandler / SecurityConfig）及需要文本的框架组件使用。
 * <p>
 * 依赖注入的组件建议直接注入 MessageSource；本门面解决两类场景：
 * <ol>
 *   <li>静态调用点（异常处理器之外的工具类/切面）无法注入；</li>
 *   <li>统一兜底策略：当前 Locale 缺 key → 回退中文（zh_CN 基准文件）→ 回退调用方默认文案 → key 原样，永不抛错。</li>
 * </ol>
 * Locale 来源：Web 请求线程取 {@link LocaleContextHolder}（由 DispatcherServlet 按
 * Accept-Language 写入）；非 Web 线程（定时任务/MQ 消费）取 {@link I18nConfig#DEFAULT_LOCALE}。
 * Spring Security 过滤器链早于 DispatcherServlet，此时请用
 * {@link #getMessage(String, Object[], Locale, String)} 显式传 Locale。
 */
public final class AuthMessages {

    /** 兜底顺序中的中文基准 Locale */
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;

    /** 由 I18nConfig 创建 MessageSource Bean 时绑定；单测/未装配场景为 null（走默认文案） */
    private static volatile MessageSource messageSource;

    private AuthMessages() {
    }

    /** 绑定消息源（I18nConfig 在 Bean 创建时调用；devtools 重启时重复绑定安全） */
    public static void bind(MessageSource source) {
        messageSource = source;
    }

    /** 当前 Locale 是否已初始化（供排查"消息为何没本地化"） */
    public static boolean isBound() {
        return messageSource != null;
    }

    /** 取当前请求 Locale 的文案（无占位参数） */
    public static String getMessage(String key) {
        return getMessage(key, null, currentLocale(), null);
    }

    /** 取当前请求 Locale 的文案，{0}{1}... 占位参数填充 */
    public static String getMessage(String key, Object... args) {
        return getMessage(key, args, currentLocale(), null);
    }

    /** 按错误码枚举取文案（key=errorCode.codeKey，缺 key 回退枚举默认中文） */
    public static String getMessage(ErrorCode errorCode) {
        return getMessage(errorCode.getCodeKey(), null, currentLocale(), errorCode.getMessage());
    }

    /**
     * 全量版：显式 Locale + 默认文案。
     * 兜底链：目标 Locale → zh_CN → defaultMessage → key 原样。
     *
     * @param key            消息 key（MessageKeys 常量）
     * @param args           占位参数（可为 null）
     * @param locale         目标 Locale（null 视为当前请求 Locale）
     * @param defaultMessage 全部缺失时的兜底文案（可为 null，则返回 key 原样）
     */
    public static String getMessage(String key, Object[] args, Locale locale, String defaultMessage) {
        MessageSource source = messageSource;
        if (source == null) {
            return defaultMessage != null ? defaultMessage : key;
        }
        Locale target = locale != null ? locale : currentLocale();
        try {
            return source.getMessage(key, args, target);
        } catch (NoSuchMessageException ex) {
            // 当前语言缺失（如 messages_en 漏补）→ 回退中文基准文件
            try {
                return source.getMessage(key, args, ZH_CN);
            } catch (NoSuchMessageException ex2) {
                return defaultMessage != null ? defaultMessage : key;
            }
        }
    }

    /**
     * 当前应使用的 Locale：Web 请求线程 → LocaleContextHolder（Accept-Language 解析结果）；
     * 非 Web 线程 → 中文默认（与产品主语言一致）。
     */
    public static Locale currentLocale() {
        if (RequestContextHolder.getRequestAttributes() != null) {
            return LocaleContextHolder.getLocale();
        }
        return I18nConfig.DEFAULT_LOCALE;
    }
}
