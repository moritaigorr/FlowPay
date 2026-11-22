package com.flowpay.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowpay.FlowPayApplication;
import com.flowpay.reporting.application.DashboardService;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = FlowPayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FinancialTitlePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FinancialTitleService titles;
    @Autowired DashboardService dashboard;

    private UUID tenantId;
    private UUID accountId;
    private UUID contactId;
    private UUID categoryId;
    private UUID titleId;
    private String email;

    @BeforeEach
    void seedTitle() {
        tenantId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        titleId = UUID.randomUUID();
        var today = LocalDate.now();
        email = "integration-" + tenantId + "@flowpay.local";

        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, ?)", tenantId, "Integration test");
        jdbc.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, ?)", userId, email, "not-used");
        jdbc.update("INSERT INTO membership (tenant_id, user_id, role) VALUES (?, ?, 'OWNER')", tenantId, userId);
        jdbc.update("INSERT INTO contact (id, tenant_id, name, is_customer) VALUES (?, ?, 'Cliente teste', true)", contactId, tenantId);
        jdbc.update("INSERT INTO financial_category (id, tenant_id, name, direction) VALUES (?, ?, 'Receita teste', 'INFLOW')", categoryId, tenantId);
        jdbc.update("""
                INSERT INTO financial_account (id, tenant_id, name, account_type, opening_balance_date, creation_key, creation_payload_hash)
                VALUES (?, ?, 'Conta teste', 'BANK', ?, ?, ?)
                """, accountId, tenantId, today, UUID.randomUUID(), "a".repeat(64));
        jdbc.update("""
                INSERT INTO financial_title
                  (id, tenant_id, kind, description, contact_id, category_id, category_direction, planned_account_id,
                   original_amount_minor, due_date, creation_key, creation_payload_hash)
                VALUES (?, ?, 'RECEIVABLE', 'Serviço teste', ?, ?, 'INFLOW', ?, 10000, ?, ?, ?)
                """, titleId, tenantId, contactId, categoryId, accountId, today, UUID.randomUUID(), "b".repeat(64));
    }

    @Test
    void concurrentPartialSettlementsCannotExceedTitleBalance() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> settleTogether(ready, start));
            var second = executor.submit(() -> settleTogether(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var outcomes = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("committed", "conflict");
            assertThat(count("SELECT count(*) FROM financial_settlement WHERE tenant_id = ?", tenantId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id = ? AND origin = 'TITLE_SETTLEMENT'", tenantId)).isEqualTo(1);
            assertThat(titles.get(email, titleId).settledAmountMinor()).isEqualTo("7000");
            assertThat(titles.get(email, titleId).remainingAmountMinor()).isEqualTo("3000");
        }
    }

    @Test
    void settlementFailureRollsBackItsCashMovement() {
        jdbc.execute("""
                CREATE FUNCTION reject_test_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'forced settlement persistence failure'; END;
                $$
                """);
        jdbc.execute("CREATE TRIGGER reject_test_settlement BEFORE INSERT ON financial_settlement FOR EACH ROW EXECUTE FUNCTION reject_test_settlement()");
        try {
            assertThatThrownBy(() -> titles.settle(email, titleId, UUID.randomUUID(), new FinancialTitleService.SettlementInput(accountId, 7000, LocalDate.now())))
                    .isInstanceOf(RuntimeException.class);
            assertThat(count("SELECT count(*) FROM financial_settlement WHERE tenant_id = ?", tenantId)).isZero();
            assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id = ? AND origin = 'TITLE_SETTLEMENT'", tenantId)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_test_settlement ON financial_settlement");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_test_settlement()");
        }
    }

    @Test
    void dashboardSeparatesForecastFromMovementsAndScopesTenant() {
        var today = LocalDate.now();
        var otherTenantId = UUID.randomUUID();
        var otherAccountId = UUID.randomUUID();
        var otherCategoryId = UUID.randomUUID();
        var otherMovementId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_transaction (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description)
                VALUES (?, ?, ?, 'INFLOW', 50000, ?, 'OPENING_BALANCE', 'Saldo inicial')
                """, UUID.randomUUID(), tenantId, accountId, today);
        titles.settle(email, titleId, UUID.randomUUID(), new FinancialTitleService.SettlementInput(accountId, 7000, today));
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, 'Other tenant')", otherTenantId);
        jdbc.update("INSERT INTO financial_category (id, tenant_id, name, direction) VALUES (?, ?, 'Other income', 'INFLOW')", otherCategoryId, otherTenantId);
        jdbc.update("""
                INSERT INTO financial_account (id, tenant_id, name, account_type, opening_balance_date, creation_key, creation_payload_hash)
                VALUES (?, ?, 'Other account', 'BANK', ?, ?, ?)
                """, otherAccountId, otherTenantId, today, UUID.randomUUID(), "d".repeat(64));
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description,
                   category_id, category_direction, idempotency_key, payload_hash)
                VALUES (?, ?, ?, 'INFLOW', 99000, ?, 'MANUAL', 'Other tenant movement', ?, 'INFLOW', ?, ?)
                """, otherMovementId, otherTenantId, otherAccountId, today, otherCategoryId, UUID.randomUUID(), "e".repeat(64));
        try {
            var result = dashboard.get(email, today, today, "America/Sao_Paulo");

            assertThat(result.controlledBalanceMinor()).isEqualTo("57000");
            assertThat(result.periodInflowsMinor()).isEqualTo("7000");
            assertThat(result.periodOutflowsMinor()).isEqualTo("0");
            assertThat(result.receivablesDueMinor()).isEqualTo("3000");
            assertThat(result.payablesDueMinor()).isEqualTo("0");
            assertThat(result.asOf()).isEqualTo(today);
            assertThat(result.timezone()).isEqualTo("America/Sao_Paulo");
            assertThat(result.recentMovements()).hasSize(2);
        } finally {
            jdbc.update("DELETE FROM cash_transaction WHERE tenant_id = ?", otherTenantId);
            jdbc.update("DELETE FROM financial_account WHERE tenant_id = ?", otherTenantId);
            jdbc.update("DELETE FROM financial_category WHERE tenant_id = ?", otherTenantId);
            jdbc.update("DELETE FROM tenant WHERE id = ?", otherTenantId);
        }
    }

    private String settleTogether(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test start timed out");
        try {
            titles.settle(email, titleId, UUID.randomUUID(), new FinancialTitleService.SettlementInput(accountId, 7000, LocalDate.now()));
            return "committed";
        } catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode() != HttpStatus.CONFLICT) throw conflict;
            return "conflict";
        }
    }

    private long count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }
}
