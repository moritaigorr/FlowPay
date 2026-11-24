package com.flowpay.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowpay.FlowPayApplication;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = FlowPayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({"local", "demo"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DemoSeedPostgresIntegrationTest {

    private static final String DEMO_PASSWORD = "FlowPaySeed-Testing-2026!";
    private static final String OTHER_PASSWORD = "OtherTenant-Testing-2026!";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flowpay");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("FLOWPAY_DEMO_PASSWORD", () -> DEMO_PASSWORD);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationRunner seedRunner;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvc mockMvc;

    @Test
    void demoSessionReadsSeededDataAndValidCsrfProtectsLogin() throws Exception {
        CsrfState csrf = csrfState();
        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@flowpay.local\",\"password\":\"FlowPaySeed-Testing-2026!\"}"))
                .andExpect(status().isForbidden());

        MockHttpSession session = login("demo@flowpay.local", DEMO_PASSWORD, csrf);

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@flowpay.local"))
                .andExpect(jsonPath("$.companyName").value("FlowPay Demonstração"))
                .andExpect(jsonPath("$.role").value("OWNER"));
        mockMvc.perform(get("/api/contacts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/dashboard").param("timezone", "UTC").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controlledBalanceMinor").value("2500000"));
    }

    @Test
    void authenticatedDifferentTenantCannotReadOrMutateDemoData() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id, email, password_hash) VALUES (?, ?, ?)", otherUserId,
                "other-tenant@flowpay.local", passwordEncoder.encode(OTHER_PASSWORD));
        jdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)", otherTenantId, "Outra empresa de teste");
        jdbc.update("INSERT INTO membership(tenant_id, user_id, role) VALUES (?, ?, 'OWNER')",
                otherTenantId, otherUserId);
        UUID demoContactId = jdbc.queryForObject(
                "SELECT id FROM contact WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração') LIMIT 1",
                UUID.class);

        CsrfState csrf = csrfState();
        MockHttpSession session = login("other-tenant@flowpay.local", OTHER_PASSWORD, csrf);

        mockMvc.perform(get("/api/contacts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/contacts/{id}", demoContactId).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/contacts/{id}", demoContactId)
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alterado por outro tenant\",\"roles\":[\"CUSTOMER\"]}"))
                .andExpect(status().isNotFound());
        MockMultipartFile foreignAccountCsv = new MockMultipartFile("file", "statement.csv", "text/csv",
                "data;descricao;valor;direcao\n23/11/2026;Recebimento;10,00;ENTRADA\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/statement-imports/preview")
                        .file(foreignAccountCsv)
                        .param("accountId", demoAccountId().toString())
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT name FROM contact WHERE id = ?", String.class, demoContactId))
                .isEqualTo("Cliente Exemplo Ltda.");
        assertThat(count("SELECT count(*) FROM statement_import WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isZero();
        mockMvc.perform(get("/api/dashboard").param("timezone", "UTC").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controlledBalanceMinor").value("0"));
    }

    @Test
    void localProfileAllowsRegistrationWithValidCsrf() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(true));

        var email = "registration-" + UUID.randomUUID() + "@flowpay.local";
        var csrf = csrfState();

        mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"companyName\":\"Empresa de teste\",\"password\":\"Long-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Empresa de teste"));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM membership m
                JOIN app_user u ON u.id = m.user_id
                WHERE u.email = ? AND m.role = 'OWNER'
                """, Long.class, email)).isEqualTo(1);
    }

    @Test
    @Transactional
    void internalTransferMovesValueBetweenAccountsWithoutChangingCompanyCashOrOperatingFlows() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        String email = "transfer-" + UUID.randomUUID() + "@flowpay.local";
        String password = "Transfer-Testing-2026!";
        jdbc.update("INSERT INTO app_user(id, email, password_hash) VALUES (?, ?, ?)", userId, email, passwordEncoder.encode(password));
        jdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)", tenantId, "Transfer test company");
        jdbc.update("INSERT INTO membership(tenant_id, user_id, role) VALUES (?, ?, 'OWNER')", tenantId, userId);
        insertAccount(tenantId, sourceAccountId, "Source account");
        insertAccount(tenantId, destinationAccountId, "Destination account");
        insertOpeningBalance(tenantId, sourceAccountId, 10_000);
        insertOpeningBalance(tenantId, destinationAccountId, 5_000);

        CsrfState csrf = csrfState();
        MockHttpSession session = login(email, password, csrf);
        UUID key = UUID.randomUUID();
        String request = "{\"sourceAccountId\":\"" + sourceAccountId + "\",\"destinationAccountId\":\""
                + destinationAccountId + "\",\"amountMinor\":\"2000\",\"effectiveDate\":\"2026-11-24\",\"description\":\"Transfer between own accounts\"}";

        mockMvc.perform(post("/api/cash-transactions/transfers")
                        .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outgoingMovement.origin").value("TRANSFER"))
                .andExpect(jsonPath("$.outgoingMovement.direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.outgoingMovement.counterpartyAccountName").value("Destination account"))
                .andExpect(jsonPath("$.incomingMovement.direction").value("INFLOW"));

        mockMvc.perform(post("/api/cash-transactions/transfers")
                        .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replay").value(true));

        mockMvc.perform(post("/api/cash-transactions/transfers")
                        .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(request.replace("2000", "3000")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/cash-transactions/transfers")
                        .session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace(destinationAccountId.toString(), sourceAccountId.toString())))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cash_transfer WHERE tenant_id = ?", Long.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cash_transaction WHERE tenant_id = ? AND origin = 'TRANSFER'", Long.class, tenantId)).isEqualTo(2);
        assertThat(accountBalance(tenantId, sourceAccountId)).isEqualTo(8_000L);
        assertThat(accountBalance(tenantId, destinationAccountId)).isEqualTo(5_000L + 2_000L);
        assertThat(jdbc.queryForObject("SELECT sum(CASE direction WHEN 'INFLOW' THEN amount_minor ELSE -amount_minor END) FROM cash_transaction WHERE tenant_id = ?", Long.class, tenantId))
                .isEqualTo(15_000L);
        mockMvc.perform(get("/api/dashboard").session(session).param("from", "2026-11-24").param("to", "2026-11-24").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodInflowsMinor").value("0"))
                .andExpect(jsonPath("$.periodOutflowsMinor").value("0"));
    }

    @Test
    @Transactional
    void managerCanManageBusinessDataWhileReaderHasTenantScopedReadOnlyAccess() throws Exception {
        UUID managerId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String managerEmail = "manager-" + UUID.randomUUID() + "@flowpay.local";
        String readerEmail = "reader-" + UUID.randomUUID() + "@flowpay.local";
        String password = "Role-Testing-2026!";
        jdbc.update("INSERT INTO app_user(id, email, password_hash) VALUES (?, ?, ?)", managerId, managerEmail, passwordEncoder.encode(password));
        jdbc.update("INSERT INTO app_user(id, email, password_hash) VALUES (?, ?, ?)", readerId, readerEmail, passwordEncoder.encode(password));
        jdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)", tenantId, "Role policy test company");
        jdbc.update("INSERT INTO membership(tenant_id, user_id, role) VALUES (?, ?, 'MANAGER')", tenantId, managerId);
        jdbc.update("INSERT INTO membership(tenant_id, user_id, role) VALUES (?, ?, 'READER')", tenantId, readerId);

        CsrfState csrf = csrfState();
        MockHttpSession managerSession = login(managerEmail, password, csrf);
        MockHttpSession readerSession = login(readerEmail, password, csrf);
        mockMvc.perform(get("/api/auth/session").session(managerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
        mockMvc.perform(get("/api/auth/session").session(readerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("READER"));

        MvcResult contactResult = mockMvc.perform(post("/api/contacts").session(managerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Manager customer\",\"roles\":[\"CUSTOMER\"],\"email\":null,\"phone\":null,\"notes\":null}"))
                .andExpect(status().isCreated()).andReturn();
        UUID contactId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(contactResult.getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(get("/api/contacts").session(readerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(post("/api/contacts").session(readerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reader must not create\",\"roles\":[\"CUSTOMER\"]}"))
                .andExpect(status().isForbidden());

        MvcResult accountResult = mockMvc.perform(post("/api/financial-accounts").session(managerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Manager account\",\"type\":\"BANK\",\"openingBalanceMinor\":\"0\",\"openingBalanceDate\":\"2026-11-24\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID accountId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(accountResult.getResponse().getContentAsString(), "$.id"));
        MvcResult categoryResult = mockMvc.perform(post("/api/financial-categories").session(managerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Manager inflow\",\"direction\":\"INFLOW\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID categoryId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.id"));
        String movement = "{\"direction\":\"INFLOW\",\"accountId\":\"" + accountId + "\",\"categoryId\":\"" + categoryId
                + "\",\"amountMinor\":\"1500\",\"effectiveDate\":\"2026-11-24\",\"description\":\"Manager inflow\"}";
        mockMvc.perform(post("/api/cash-transactions").session(managerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(movement))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/cash-transactions").session(readerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(movement))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cash-transactions").session(readerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));

        String title = "{\"kind\":\"RECEIVABLE\",\"description\":\"Manager title\",\"contactId\":\"" + contactId
                + "\",\"categoryId\":\"" + categoryId + "\",\"originalAmountMinor\":\"2500\",\"dueDate\":\"2026-12-01\"}";
        mockMvc.perform(post("/api/financial-titles").session(managerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(title))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/financial-titles").session(readerSession).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(title))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedStatementPreviewRequiresCsrfAndDoesNotPersistWithoutIt() throws Exception {
        UUID accountId = demoAccountId();
        CsrfState csrf = csrfState();
        MockHttpSession session = login("demo@flowpay.local", DEMO_PASSWORD, csrf);
        MockMultipartFile csv = new MockMultipartFile("file", "statement.csv", "text/csv",
                "data;descricao;valor;direcao\n23/11/2026;Recebimento;10,00;ENTRADA\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/statement-imports/preview")
                        .file(csv)
                        .param("accountId", accountId.toString())
                        .session(session)
                        .cookie(csrf.cookie()))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM statement_import WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isZero();
    }

    @Test
    void authenticatedStatementPreviewRejectsFilesAboveTheConfiguredLimit() throws Exception {
        UUID accountId = demoAccountId();
        CsrfState csrf = csrfState();
        MockHttpSession session = login("demo@flowpay.local", DEMO_PASSWORD, csrf);
        byte[] oversizedCsv = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile csv = new MockMultipartFile("file", "statement.csv", "text/csv", oversizedCsv);

        mockMvc.perform(multipart("/api/statement-imports/preview")
                        .file(csv)
                        .param("accountId", accountId.toString())
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM statement_import WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isZero();
    }

    @Test
    void createsKnownSyntheticStateAndRerunsWithoutDuplicatingRows() throws Exception {
        assertSeedState();

        ApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
        seedRunner.run(arguments);

        assertSeedState();
    }

    private void assertSeedState() {
        assertThat(count("SELECT count(*) FROM app_user WHERE email = 'demo@flowpay.local'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM tenant WHERE name = 'FlowPay Demonstração'")).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM membership m
                JOIN app_user u ON u.id = m.user_id
                JOIN tenant t ON t.id = m.tenant_id
                WHERE u.email = 'demo@flowpay.local' AND t.name = 'FlowPay Demonstração' AND m.role = 'OWNER'
                """)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM contact WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM financial_account WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM financial_category WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM financial_title WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id = (SELECT id FROM tenant WHERE name = 'FlowPay Demonstração')"))
                .isEqualTo(1);

        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM app_user WHERE email = 'demo@flowpay.local'", String.class);
        assertThat(passwordHash).isNotEqualTo(DEMO_PASSWORD);
        assertThat(passwordEncoder.matches(DEMO_PASSWORD, passwordHash)).isTrue();
    }

    private void insertAccount(UUID tenantId, UUID id, String name) {
        jdbc.update("""
                INSERT INTO financial_account(id, tenant_id, name, account_type, opening_balance_date, creation_key, creation_payload_hash)
                VALUES (?, ?, ?, 'BANK', DATE '2026-11-24', ?, ?)
                """, id, tenantId, name, UUID.randomUUID(), "0".repeat(64));
    }

    private void insertOpeningBalance(UUID tenantId, UUID accountId, long amountMinor) {
        jdbc.update("""
                INSERT INTO cash_transaction(id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description)
                VALUES (?, ?, ?, 'INFLOW', ?, DATE '2026-11-24', 'OPENING_BALANCE', 'Opening balance')
                """, UUID.randomUUID(), tenantId, accountId, amountMinor);
    }

    private long accountBalance(UUID tenantId, UUID accountId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(CASE direction WHEN 'INFLOW' THEN amount_minor ELSE -amount_minor END), 0)
                FROM cash_transaction WHERE tenant_id = ? AND financial_account_id = ?
                """, Long.class, tenantId, accountId);
    }

    private long count(String query) {
        return jdbc.queryForObject(query, Long.class);
    }

    private UUID demoAccountId() {
        return jdbc.queryForObject("""
                SELECT a.id FROM financial_account a
                JOIN tenant t ON t.id = a.tenant_id
                WHERE t.name = 'FlowPay Demonstração'
                """, UUID.class);
    }

    private CsrfState csrfState() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new CsrfState(cookie, cookie.getValue());
    }

    private MockHttpSession login(String email, String password, CsrfState csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private record CsrfState(Cookie cookie, String token) {}
}
