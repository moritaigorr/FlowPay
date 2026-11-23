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
                .andExpect(jsonPath("$.companyName").value("FlowPay Demonstração"));
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
