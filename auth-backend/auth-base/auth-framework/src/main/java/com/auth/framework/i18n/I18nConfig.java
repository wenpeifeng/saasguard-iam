package com.auth.framework.i18n;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 国际化配置（MessageSource + LocaleResolver）。
 * <p>
 * 消息文件：erp-bootstrap/src/main/resources/i18n/messages_zh_CN.properties（中文基准）
 * 与 messages_en.properties（英文）；key 常量见 com.auth.common.i18n.MessageKeys。
 * <ul>
 *   <li><b>MessageSource</b>：ResourceBundleMessageSource，basename/编码取自
 *       application.yml 的 spring.messages.*（默认 i18n/messages + UTF-8，与 Spring Boot
 *       自动配置语义一致）；useCodeAsDefaultMessage=false，缺失 key 的兜底（中文 → 默认文案）
 *       统一由 {@link AuthMessages} 处理。</li>
 *   <li><b>LocaleResolver</b>：按请求头 Accept-Language 解析（zh/zh-CN→zh_CN，en/en-US→en，
 *       缺失或不支持的语言回退 zh_CN）。Bean 名必须是 {@code localeResolver}，
 *       DispatcherServlet 才能识别。</li>
 * </ul>
 * Spring Boot 的 MessageSourceAutoConfiguration 在存在自定义 MessageSource Bean 时自动退让，
 * 因此 spring.messages.* 的值由本配置显式读取，保证 yml 与实际生效值一致。
 */
@Configuration
public class I18nConfig {

    /** 默认语言：中文（简体）—— Accept-Language 缺失/不支持时的兜底 */
    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /**
     * 消息源：i18n/messages*（classpath，erp-bootstrap 打包）。
     * 绑定到静态门面 {@link AuthMessages}，供异常体系等无法注入的场景使用。
     */
    @Bean
    public MessageSource messageSource(
            @Value("${spring.messages.basename:i18n/messages}") String basename,
            @Value("${spring.messages.encoding:UTF-8}") String encoding,
            @Value("${spring.messages.fallback-to-system-locale:false}") boolean fallbackToSystemLocale) {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(basename.split("[,\\s]+"));
        source.setDefaultEncoding(encoding != null ? encoding : StandardCharsets.UTF_8.name());
        // 不回退系统 Locale（本机语言不可控），统一回退 DEFAULT_LOCALE（zh_CN）
        source.setFallbackToSystemLocale(fallbackToSystemLocale);
        // 缺失 key 不抛 NoSuchMessageException 的兜底交给 AuthMessages（中文→默认文案→key 原样）
        source.setUseCodeAsDefaultMessage(false);
        AuthMessages.bind(source);
        return source;
    }

    /**
     * Locale 解析器：读 Accept-Language 请求头。
     * 支持中文（zh/zh-CN/zh-TW 按语言匹配 zh_CN）与英文（en/en-US/en-GB→en），
     * 其余（fr/ja/未带）一律回退中文。
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH));
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        return resolver;
    }
}
