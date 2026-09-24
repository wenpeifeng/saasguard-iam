package com.auth.common.i18n;

/**
 * 消息 Key 常量 —— 后端国际化文案的唯一登记处（与
 * erp-bootstrap/src/main/resources/i18n/messages_zh_CN.properties、
 * messages_en.properties 一一对应）。
 * <p>
 * 命名规范（详见 docs/modules/i18n.md）：
 * <ul>
 *   <li>{@code auth.error.*}：框架层/错误码枚举（ErrorCode.codeKey）文案；</li>
 *   <li>{@code auth.biz.*}：跨模块高频业务提示（库存不足/请勿重复提交/期间已结账 等），
 *       业务代码 {@code throw BusinessException.ofKey(MessageKeys.XXX, args)} 引用；</li>
 *   <li>仅单模块使用的文案建议放各模块前缀（如 {@code erp.inv.*}），避免本类膨胀。</li>
 * </ul>
 * 注意：占位符用 MessageFormat 风格 {0}/{1}；英文文案含 ' 时需写成 ''（MessageFormat 转义）。
 * <p>
 * 本类为纯 Java 常量（erp-common 不依赖 Spring），消息解析由 erp-framework 的
 * {@code AuthMessages} 门面按请求 Locale（Accept-Language）完成。
 */
public final class MessageKeys {

    private MessageKeys() {
    }

    // ================= auth.error.* 框架层（与 ErrorCode.codeKey 一致） =================

    public static final String SUCCESS = "auth.error.success";
    public static final String BAD_REQUEST = "auth.error.bad-request";
    public static final String UNAUTHORIZED = "auth.error.unauthorized";
    public static final String FORBIDDEN = "auth.error.forbidden";
    public static final String NOT_FOUND = "auth.error.not-found";
    public static final String METHOD_NOT_ALLOWED = "auth.error.method-not-allowed";
    public static final String INTERNAL_ERROR = "auth.error.internal-error";
    public static final String SERVICE_UNAVAILABLE = "auth.error.service-unavailable";
    public static final String BUSINESS_ERROR = "auth.error.business-error";
    public static final String DUPLICATE_DATA = "auth.error.duplicate-data";
    public static final String DATA_NOT_EXISTS = "auth.error.data-not-exists";
    public static final String DATA_IN_USE = "auth.error.data-in-use";

    // ================= auth.error.* 框架处理器补充文案（带参数） =================

    /** 参数校验失败（{0}=具体字段错误拼接） */
    public static final String VALIDATION_FAILED = "auth.error.validation-failed";
    /** 缺少必填请求参数（{0}=参数名） */
    public static final String MISSING_PARAM = "auth.error.missing-param";
    /** 参数类型不匹配（{0}=参数名） */
    public static final String TYPE_MISMATCH = "auth.error.type-mismatch";
    /** 接口不存在（{0}=请求路径） */
    public static final String NOT_FOUND_DETAIL = "auth.error.not-found-detail";

    // ================= auth.biz.* 通用业务提示（高频文案） =================

    // ---- 认证 / 权限 ----
    /** 账号或密码错误 */
    public static final String BIZ_LOGIN_FAILED = "auth.biz.login-failed";
    /** 账号锁定（{0}=重试等待说明） */
    public static final String BIZ_ACCOUNT_LOCKED = "auth.biz.account-locked";
    /** 账号已停用 */
    public static final String BIZ_ACCOUNT_DISABLED = "auth.biz.account-disabled";
    /** 权限不足 */
    public static final String BIZ_PERMISSION_DENIED = "auth.biz.permission-denied";
    /** 未登录或登录已过期 */
    public static final String BIZ_NOT_LOGGED_IN = "auth.biz.not-logged-in";

    // ---- 通用数据 ----
    /** 数据不存在 */
    public static final String BIZ_DATA_NOT_EXISTS = "auth.biz.data-not-exists";
    /** 数据已存在，请勿重复提交 */
    public static final String BIZ_DATA_EXISTS = "auth.biz.data-exists";
    /** 请勿重复提交 */
    public static final String BIZ_DUPLICATE_SUBMIT = "auth.biz.duplicate-submit";
    /** 数据被引用，无法删除 */
    public static final String BIZ_DATA_IN_USE = "auth.biz.data-in-use";
    /** 编码已存在（{0}=编码值） */
    public static final String BIZ_CODE_EXISTS = "auth.biz.code-exists";
    /** 名称已存在（{0}=名称值） */
    public static final String BIZ_NAME_EXISTS = "auth.biz.name-exists";
    /** 并发冲突，请刷新后重试 */
    public static final String BIZ_CONCURRENT_CONFLICT = "auth.biz.concurrent-conflict";
    /** 保存失败 */
    public static final String BIZ_SAVE_FAILED = "auth.biz.save-failed";

    // ---- 单据通用 ----
    /** 单据不存在（{0}=单据编号或 id） */
    public static final String BIZ_DOC_NOT_EXISTS = "auth.biz.doc-not-exists";
    /** 单据已审核，请勿重复审核（{0}=单据编号） */
    public static final String BIZ_DOC_AUDITED = "auth.biz.doc-audited";
    /** 单据未审核，请先审核（{0}=单据编号） */
    public static final String BIZ_DOC_NOT_AUDITED = "auth.biz.doc-not-audited";
    /** 单据已审核过账，不允许删除/修改（{0}=单据编号） */
    public static final String BIZ_DOC_POSTED = "auth.biz.doc-posted";
    /** 单据无明细行，无法审核 */
    public static final String BIZ_DOC_NO_LINES = "auth.biz.doc-no-lines";
    /** 当前状态不允许该操作 */
    public static final String BIZ_STATUS_NOT_ALLOWED = "auth.biz.status-not-allowed";
    /** 期间已结账，不允许改动（{0}=期间） */
    public static final String BIZ_PERIOD_CLOSED = "auth.biz.period-closed";

    // ---- 库存 ----
    /** 库存不足（{0}=仓库，{1}=物料，{2}=数量说明） */
    public static final String BIZ_STOCK_NOT_ENOUGH = "auth.biz.stock-not-enough";
    /** 指定批次库存不足（{0}=仓库，{1}=物料，{2}=批次） */
    public static final String BIZ_BATCH_STOCK_NOT_ENOUGH = "auth.biz.batch-stock-not-enough";
    /** 数量必须大于 0 */
    public static final String BIZ_QTY_POSITIVE = "auth.biz.qty-positive";
    /** 调出仓与调入仓不能相同 */
    public static final String BIZ_SAME_WAREHOUSE = "auth.biz.same-warehouse";
    /** 启用批次管理，必须指定批次号（{0}=物料） */
    public static final String BIZ_BATCH_REQUIRED = "auth.biz.batch-required";
    /** 未启用批次管理，不能指定批次出库（{0}=物料） */
    public static final String BIZ_BATCH_NOT_ALLOWED = "auth.biz.batch-not-allowed";

    // ---- 通用操作 ----
    /** 操作过于频繁，请稍后再试 */
    public static final String BIZ_TOO_FREQUENT = "auth.biz.too-frequent";
    /** 导出失败 */
    public static final String BIZ_EXPORT_FAILED = "auth.biz.export-failed";
    /** 导入失败：文件格式错误 */
    public static final String BIZ_IMPORT_FORMAT_ERROR = "auth.biz.import-format-error";
    /** 已停用，不允许操作 */
    public static final String BIZ_ALREADY_DISABLED = "auth.biz.already-disabled";
}
