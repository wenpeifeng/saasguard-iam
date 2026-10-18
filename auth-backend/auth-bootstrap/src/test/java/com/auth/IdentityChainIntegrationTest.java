package com.auth;

import com.auth.idm.dto.UserForm;
import com.auth.idm.service.IdmUserService;
import com.auth.system.service.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

/** 真 MySQL/Flyway + 独立 Redis + HTTP 过滤器的身份链路回归；每次创建并清理独立测试库。 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "AUTH_CHAIN_TESTS", matches = "true")
class IdentityChainIntegrationTest {
    static final String DB = "auth_chain_test_" + UUID.randomUUID().toString().replace("-", "");
    static final String MYSQL = "jdbc:mysql://localhost:13306/";
    static final String OPTIONS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    static final String PASSWORD = System.getenv().getOrDefault("AUTH_TEST_MYSQL_PASSWORD", "auth123456");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdmUserService users;
    @Autowired com.auth.app.service.AppAssignService assignments;
    @MockBean AuditService audit;
    String admin;

    @DynamicPropertySource
    static void isolatedInfrastructure(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL + OPTIONS, "root", PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DB + " CHARACTER SET utf8mb4");
        }
        properties.add("spring.datasource.url", () -> MYSQL + DB + OPTIONS);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.flyway.locations", () -> "filesystem:../../script/sql/migration");
        properties.add("spring.data.redis.port", () -> System.getenv("AUTH_TEST_REDIS_PORT"));
        properties.add("spring.data.redis.password", () -> "chain-test-only");
        // 推送链路由下游系统独立验证；本套件只验证主档、租户与协议链路，避免依赖 5172/5173。
        properties.add("identity.sync.enabled", () -> "false");
    }

    @AfterAll
    static void dropOwnDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL + OPTIONS, "root", PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DB);
        }
    }

    @BeforeEach
    void loginAdmin() throws Exception { admin = login("admin", "123456"); }

    @Test
    void organizationTypesCodesAndHierarchy() throws Exception {
        long factory = org("FACTORY", 1);
        long department = org("DEPARTMENT", factory);
        long workshop = org("WORKSHOP", factory);
        assertThat(jdbc.queryForObject("SELECT code FROM idm_org WHERE id=?", String.class, department)).startsWith("DEPT-");
        assertThat(jdbc.queryForObject("SELECT code FROM idm_org WHERE id=?", String.class, workshop)).startsWith("WS-");
        fail(call("POST", "/idm/orgs", Map.of("name", "非法类型", "orgType", "UNKNOWN", "parentId", 1), admin));
        fail(call("POST", "/idm/orgs", Map.of("name", "车间无上级", "orgType", "WORKSHOP", "parentId", 0), admin));
        fail(call("PUT", "/idm/orgs/" + factory, Map.of("name", "环", "orgType", "FACTORY", "parentId", department), admin));
        String before = jdbc.queryForObject("SELECT code FROM idm_org WHERE id=?", String.class, workshop);
        ok(call("PUT", "/idm/orgs/" + workshop, Map.of("name", "挂靠部门后的车间", "orgType", "WORKSHOP", "parentId", department), admin));
        assertThat(jdbc.queryForObject("SELECT code FROM idm_org WHERE id=?", String.class, workshop)).isEqualTo(before);
        fail(call("DELETE", "/idm/orgs/" + factory, null, admin));
        long empty = org("TEAM", workshop);
        ok(call("DELETE", "/idm/orgs/" + empty, null, admin));
        long next = org("TEAM", workshop);
        assertThat(next).isNotEqualTo(empty);
    }

    @Test
    void employeeToApplicationAndOffboarding() throws Exception {
        long factory = org("FACTORY", 1);
        long workshop = org("WORKSHOP", factory);
        Map<String, Object> form = new LinkedHashMap<>(Map.of("realName", "链路测试员工", "orgId", factory, "password", "123456", "entryDate", "2026-01-01"));
        long id = ok(call("POST", "/idm/users", form, admin)).asLong();
        JsonNode user = ok(call("GET", "/idm/users/" + id, null, admin));
        String number = user.path("employeeNo").asText();
        assertThat(number).startsWith("EMP-");
        assertThat(user.has("password")).isFalse();
        form.remove("password");
        form.put("realName", "修改档案无需密码");
        ok(call("PUT", "/idm/users/" + id, form, admin));
        String loginToken = login(number, "123456");
        fail(authorize(loginToken));
        ok(call("POST", "/apps/demo-client/assignments", Map.of("userIds", List.of(id)), admin));
        ok(call("POST", "/apps/demo-client/assignments", Map.of("userIds", List.of(id)), admin));
        String rolePath = "/apps/demo-client/assignments/" + id + "/roles/demo.admin";
        ok(call("PUT", rolePath, null, admin));
        ok(call("DELETE", rolePath, null, admin));
        ok(call("PUT", rolePath, null, admin));
        String access = accessToken(loginToken);
        assertThat(call("GET", "/oauth/userinfo", null, access).path("employee_no").asText()).isEqualTo(number);
        ok(call("PUT", "/apps/demo-client/assignments/" + id + "/status/false", null, admin));
        fail(call("GET", "/oauth/userinfo", null, access));
        fail(authorize(loginToken));
        ok(call("PUT", "/apps/demo-client/assignments/" + id + "/status/true", null, admin));
        fail(call("GET", "/oauth/userinfo", null, access));
        access = accessToken(loginToken);
        ok(call("DELETE", "/apps/demo-client/assignments/" + id, null, admin));
        assertThat(assignments.syncUsers("demo-client", java.time.LocalDateTime.now().minusSeconds(5)))
                .anyMatch(row -> number.equals(row.getEmployeeNo()) && "REVOKED".equals(row.getInactiveReason()));
        fail(call("GET", "/oauth/userinfo", null, access));
        assertThat(jdbc.queryForObject("SELECT deleted FROM app_user_assign WHERE user_id=? AND client_id='demo-client'", Integer.class, id)).isEqualTo(1);
        ok(call("POST", "/apps/demo-client/assignments", Map.of("userIds", List.of(id)), admin));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user_assign WHERE user_id=? AND client_id='demo-client'", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user_role WHERE user_id=? AND deleted=0", Integer.class, id)).isZero();
        access = accessToken(loginToken);
        ok(call("PUT", "/idm/users/" + id + "/transfer", Map.of("targetOrgId", workshop, "reason", "验证转岗"), admin));
        assertThat(ok(call("GET", "/idm/users/" + id, null, admin)).path("employeeNo").asText()).isEqualTo(number);
        fail(call("GET", "/oauth/userinfo", null, access));
        loginToken = login(number, "123456");
        // 授权码签发后状态变化，不能用旧授权码绕过状态校验。
        String pendingCode = code(loginToken);
        ok(call("PUT", "/idm/users/" + id + "/status/3", null, admin));
        fail(exchange(pendingCode));
        fail(call("POST", "/auth/login", Map.of("username", number, "password", "123456"), null));
        fail(call("POST", "/apps/erp-web/assignments", Map.of("userIds", List.of(id)), admin));
        ok(call("PUT", "/idm/users/" + id + "/status/1", null, admin));
        access = accessToken(login(number, "123456"));
        ok(call("PUT", "/idm/users/" + id + "/password", Map.of("password", "654321"), admin));
        fail(call("GET", "/oauth/userinfo", null, access));
        fail(call("POST", "/auth/login", Map.of("username", number, "password", "123456"), null));
        access = accessToken(login(number, "654321"));
        ok(call("PUT", "/idm/users/" + id + "/leave", null, admin));
        assertThat(assignments.syncUsers("demo-client", java.time.LocalDateTime.now().minusSeconds(5)))
                .anyMatch(row -> number.equals(row.getEmployeeNo()) && "LEFT".equals(row.getInactiveReason()));
        fail(call("GET", "/oauth/userinfo", null, access));
        fail(call("POST", "/auth/login", Map.of("username", number, "password", "654321"), null));
        fail(call("PUT", "/idm/users/" + id + "/status/1", null, admin));
        fail(call("PUT", "/apps/demo-client/assignments/" + id + "/status/true", null, admin));
        fail(call("PUT", "/idm/users/1/leave", null, admin));
        assertThat(jdbc.queryForList("SELECT event_type FROM idm_user_event WHERE user_id=?", String.class, id))
                .contains("CREATE", "UPDATE", "TRANSFER", "STATUS_CHANGE", "RESET_PWD", "LEAVE");
    }

    @Test
    void concurrentEmployeeNumbersAreUnique() throws Exception {
        long factory = org("FACTORY", 1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Long>> work = new ArrayList<>();
            for (int i = 0; i < 8; i++) work.add(() -> {
                UserForm form = new UserForm();
                form.setRealName("并发测试"); form.setOrgId(factory); form.setPassword("123456");
                return users.create(form);
            });
            Set<String> codes = new HashSet<>();
            for (var future : pool.invokeAll(work)) {
                codes.add(jdbc.queryForObject("SELECT employee_no FROM idm_user WHERE id=?", String.class, future.get()));
            }
            assertThat(codes).hasSize(8);
        }
    }

    @Test
    void disabledOrganizationsAndFormValidation() throws Exception {
        long factory = org("FACTORY", 1);
        fail(call("POST", "/idm/users", Map.of("realName", "短密码", "orgId", factory, "password", "12345"), admin));
        fail(call("POST", "/idm/users", Map.of("realName", "未来入职", "orgId", factory, "password", "123456", "entryDate", "2099-01-01"), admin));
        fail(call("POST", "/idm/users", Map.of("realName", "无效组织", "orgId", 0, "password", "123456"), admin));
        ok(call("PUT", "/idm/orgs/" + factory, Map.of("name", "停用工厂", "orgType", "FACTORY", "parentId", 1, "status", 0), admin));
        fail(call("POST", "/idm/users", Map.of("realName", "不可入职", "orgId", factory, "password", "123456"), admin));
        fail(call("POST", "/idm/orgs", Map.of("name", "不可挂靠", "orgType", "WORKSHOP", "parentId", factory), admin));
        ok(call("PUT", "/idm/orgs/" + factory, Map.of("name", "恢复工厂", "orgType", "FACTORY", "parentId", 1, "status", 1), admin));
        long id = ok(call("POST", "/idm/users", Map.of("realName", "在职员工", "orgId", factory, "password", "123456"), admin)).asLong();
        fail(call("PUT", "/idm/orgs/" + factory, Map.of("name", "仍有员工", "orgType", "FACTORY", "parentId", 1, "status", 0), admin));
        assertThat(ok(call("GET", "/idm/users/" + id + "/events", null, admin))).hasSize(1);
        fail(call("PUT", "/idm/users/1/status/3", null, admin));
    }

    @Test
    void saasTenantIsolationDelegatedAdminAndImmediateDisable() throws Exception {
        String acme = login("acme-demo", "acme_admin", "123456");

        JsonNode acmeUsers = ok(call("GET", "/idm/users?pageNum=1&pageSize=20", null, acme));
        assertThat(acmeUsers.path("records").findValuesAsText("username"))
                .containsExactly("acme_admin");
        fail(call("GET", "/tenants?pageNum=1&pageSize=20", null, acme));

        JsonNode demoUsers = ok(call("GET", "/idm/users?pageNum=1&pageSize=50", null, admin));
        assertThat(demoUsers.path("records").findValuesAsText("username"))
                .doesNotContain("acme_admin");

        JsonNode switched = ok(callAsTenant("GET", "/idm/users?pageNum=1&pageSize=20", null, admin, 2000L));
        assertThat(switched.path("records").findValuesAsText("username"))
                .containsExactly("acme_admin");

        ok(call("PUT", "/tenants/2000/status/0", null, admin));
        try {
            fail(call("GET", "/idm/users?pageNum=1&pageSize=20", null, acme));
            fail(call("POST", "/auth/login", Map.of(
                    "tenantCode", "acme-demo", "username", "acme_admin", "password", "123456"), null));
        } finally {
            ok(call("PUT", "/tenants/2000/status/1", null, admin));
        }
        assertThat(login("acme-demo", "acme_admin", "123456")).isNotBlank();
    }

    @Test
    void serviceTokensAreRevokedOnSecretResetAndApplicationDisable() throws Exception {
        JsonNode created = ok(call("POST", "/apps", Map.of(
                "clientId", "service-regression",
                "appName", "服务令牌回归应用",
                "redirectUri", "http://localhost:9099/callback",
                "accessTokenValidity", 600,
                "consentRequired", false,
                "roles", List.of()), admin));
        String secret = created.path("clientSecret").asText();
        String token = serviceToken("demo", "service-regression", secret);
        ok(call("GET", "/service/apps/service-regression/users", null, token));

        JsonNode reset = ok(call("PUT", "/apps/service-regression/secret", null, admin));
        fail(call("GET", "/service/apps/service-regression/users", null, token));

        token = serviceToken("demo", "service-regression", reset.path("clientSecret").asText());
        ok(call("PUT", "/apps/service-regression/status/0", null, admin));
        fail(call("GET", "/service/apps/service-regression/users", null, token));
        ok(call("PUT", "/apps/service-regression/status/1", null, admin));
    }

    @Test
    void tenantCreationAtomicallyBootstrapsMutableAdministratorAndLifecycleEvent() throws Exception {
        long tenantId = ok(call("POST", "/tenants", Map.ofEntries(
                Map.entry("code", "northwind"),
                Map.entry("name", "Northwind 演示租户"),
                Map.entry("planCode", "STANDARD"),
                Map.entry("primaryColor", "#3157D5"),
                Map.entry("locale", "zh-CN"),
                Map.entry("timezone", "Asia/Shanghai"),
                Map.entry("maxUsers", 20),
                Map.entry("adminUsername", "northwind_admin"),
                Map.entry("adminRealName", "Northwind 管理员"),
                Map.entry("adminPassword", "123456")), admin)).asLong();

        String tenantAdmin = login("northwind", "northwind_admin", "123456");
        JsonNode page = ok(call("GET", "/idm/users?pageNum=1&pageSize=20", null, tenantAdmin));
        JsonNode bootstrapped = page.path("records").get(0);
        assertThat(bootstrapped.path("immutable").asBoolean()).isFalse();
        assertThat(ok(call("GET", "/idm/users/" + bootstrapped.path("id").asLong() + "/events", null, tenantAdmin)))
                .anyMatch(event -> "CREATE".equals(event.path("eventType").asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE tenant_id=?", Integer.class, tenantId))
                .isEqualTo(1);
    }

    long org(String type, long parent) throws Exception {
        return ok(call("POST", "/idm/orgs", Map.of("name", type + "验证", "orgType", type, "parentId", parent), admin)).asLong();
    }
    String login(String username, String password) throws Exception {
        return login("demo", username, password);
    }
    String login(String tenantCode, String username, String password) throws Exception {
        return ok(call("POST", "/auth/login", Map.of(
                "tenantCode", tenantCode, "username", username, "password", password), null))
                .path("token").asText();
    }
    static final String VERIFIER = "a-long-random-verifier-for-integration-testing-1234567890";
    JsonNode authorize(String token) throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        return call("POST", "/oauth/authorize", Map.of("clientId", "demo-client", "redirectUri", "http://localhost:9090/callback",
                "responseType", "code", "consent", true, "codeChallenge", challenge, "codeChallengeMethod", "S256"), token);
    }
    String code(String token) throws Exception {
        String url = ok(authorize(token)).path("redirectUri").asText();
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("code");
    }
    JsonNode exchange(String code) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.post("/oauth/token").contentType("application/x-www-form-urlencoded")
                .param("grant_type", "authorization_code").param("client_id", "demo-client").param("client_secret", "demo-secret")
                .param("code", code).param("redirect_uri", "http://localhost:9090/callback").param("code_verifier", VERIFIER)).andReturn().getResponse();
        return json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }
    String accessToken(String token) throws Exception {
        JsonNode result = exchange(code(token));
        assertThat(result.hasNonNull("access_token")).as(result.toString()).isTrue();
        return result.path("access_token").asText();
    }
    String serviceToken(String tenantCode, String clientId, String clientSecret) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.post("/oauth/token")
                .contentType("application/x-www-form-urlencoded")
                .param("grant_type", "client_credentials")
                .param("tenant_code", tenantCode)
                .param("client_id", clientId)
                .param("client_secret", clientSecret)).andReturn().getResponse();
        JsonNode result = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(result.hasNonNull("access_token")).as(result.toString()).isTrue();
        return result.path("access_token").asText();
    }
    JsonNode call(String method, String path, Object body, String token) throws Exception {
        var builder = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path).contentType("application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(builder).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    JsonNode callAsTenant(String method, String path, Object body, String token, long tenantId) throws Exception {
        var builder = MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method), path)
                .contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", tenantId);
        if (body != null) builder.content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(builder).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    JsonNode ok(JsonNode result) { assertThat(result.path("code").asInt(-1)).as(result.toString()).isZero(); return result.path("data"); }
    void fail(JsonNode result) { assertThat(result.has("code") && result.path("code").asInt() != 0).as(result.toString()).isTrue(); }
}
