package com.flowpay.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowpay.FlowPayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = FlowPayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({"local", "demo"})
@Testcontainers(disabledWithoutDocker = true)
class DemoSeedPostgresIntegrationTest {

    private static final String DEMO_PASSWORD = "FlowPaySeed-Testing-2026!";

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
}
