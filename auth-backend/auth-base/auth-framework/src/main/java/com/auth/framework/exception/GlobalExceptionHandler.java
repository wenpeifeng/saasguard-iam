package com.auth.framework.exception;

import com.auth.common.exception.BusinessException;
import com.auth.common.i18n.MessageKeys;
import com.auth.common.result.ErrorCode;
import com.auth.common.result.Result;
import com.auth.framework.i18n.AuthMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 所有 Controller 抛出的异常在此统一转成 Result 响应。
 * <p>
 * 处理顺序（Spring 自动选择最匹配的 handler）：
 * <ol>
 *   <li>参数校验类（@Valid / @Validated）→ 400 + 具体字段错误信息；</li>
 *   <li>业务异常 BusinessException → 200 + 业务码（前端按 code 分支处理）；</li>
 *   <li>其余未知异常 → 500 + 系统日志（不向客户端泄露堆栈）。</li>
 * </ol>
 * 国际化：message 统一经 {@link AuthMessages} 按请求 Accept-Language 取词
 * （zh/缺省→中文，en→英文），并回填 messageKey 给前端做本地化兜底
 * （约定见 docs/modules/i18n.md）。@Valid 注解上的字面 message
 * （如"账号不能为空"）暂不参与本地化，迁移指引见文档。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @RequestBody 上的 @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBodyValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return failDetail(ErrorCode.BAD_REQUEST, MessageKeys.VALIDATION_FAILED, detail);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBind(BindException e) {
        String detail = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return failDetail(ErrorCode.BAD_REQUEST, MessageKeys.VALIDATION_FAILED, detail);
    }

    /** @RequestParam / @PathVariable 上的约束校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        return failDetail(ErrorCode.BAD_REQUEST, MessageKeys.VALIDATION_FAILED, e.getMessage());
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return failDetail(ErrorCode.BAD_REQUEST, MessageKeys.MISSING_PARAM, e.getParameterName());
    }

    /** 参数类型不匹配（如 id 传了非数字） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return failDetail(ErrorCode.BAD_REQUEST, MessageKeys.TYPE_MISMATCH, e.getName());
    }

    /** 请求方式不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return failLocalized(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 404：路径不存在（Spring Boot 3 默认关闭 throw-exception-if-no-handler，静态资源链路抛本异常） */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(Exception e) {
        String url = e instanceof NoHandlerFoundException n ? n.getRequestURL() : ((NoResourceFoundException) e).getResourcePath();
        return failDetail(ErrorCode.NOT_FOUND, MessageKeys.NOT_FOUND_DETAIL, url);
    }

    /**
     * 业务异常：可预期、可展示给用户。
     * messageKey 存在（BusinessException.ofKey / ErrorCode 枚举构造）→ 按 Locale 本地化，
     * 缺 key 回退异常自带文案；字面文案构造（存量硬编码中文）→ 原样透出，待逐步迁移。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] {} {} -> key={}, msg={}", request.getMethod(), request.getRequestURI(),
                e.getMessageKey(), e.getMessage());
        String message = e.getMessageKey() != null
                ? AuthMessages.getMessage(e.getMessageKey(), e.getMessageArgs(), AuthMessages.currentLocale(), e.getMessage())
                : e.getMessage();
        return Result.fail(e.getCode(), message, e.getMessageKey());
    }

    /**
     * 方法级权限不足（@RequirePerm/@PreAuthorize 拒绝，含 6.x 的 AuthorizationDeniedException）：
     * 异常发生在 Controller 调用链内，到不了安全过滤链的 accessDeniedHandler，
     * 需在此转 403 Result（口径与 SecurityConfig.writeForbidden 一致）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("[权限不足] {} {} -> {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return failLocalized(ErrorCode.FORBIDDEN);
    }

    /** 兜底：不可预期异常，只记日志，不泄露内部细节 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception e, HttpServletRequest request) {
        log.error("[系统异常] {} {}", request.getMethod(), request.getRequestURI(), e);
        return failLocalized(ErrorCode.INTERNAL_ERROR);
    }

    /** 按错误码枚举本地化输出（无额外参数） */
    private Result<Void> failLocalized(ErrorCode errorCode) {
        return Result.fail(errorCode.getCode(), AuthMessages.getMessage(errorCode), errorCode.getCodeKey());
    }

    /** 按错误码 + 带 {0} 占位的明细 key 本地化输出（messageKey 仍为枚举 codeKey，便于前端归类） */
    private Result<Void> failDetail(ErrorCode errorCode, String detailKey, Object... args) {
        return Result.fail(errorCode.getCode(), AuthMessages.getMessage(detailKey, args), errorCode.getCodeKey());
    }
}
