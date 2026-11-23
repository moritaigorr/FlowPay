package com.flowpay.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowpay.FlowPayApplication;
import com.flowpay.reporting.application.DashboardService;
import java.time.LocalDate;
import java.time.YearMonth;
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
import com.flowpay.reporting.application.MonthlyReportService;
import com.flowpay.statements.application.StatementPreviewService;
import com.flowpay.reconciliation.application.ReconciliationSuggestionService;
import com.flowpay.reconciliation.application.ReconciliationMatchService;
import com.flowpay.reconciliation.application.ReconciliationStatusService;
import com.flowpay.reconciliation.application.ReconciliationCandidateIgnoreService;
import com.flowpay.statements.application.StatementLineReviewService;
import com.flowpay.statements.application.StatementReconciliationSummaryService;

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
    @Autowired MonthlyReportService monthlyReports;
    @Autowired StatementPreviewService statementImports;
    @Autowired ReconciliationSuggestionService reconciliationSuggestions;
    @Autowired ReconciliationMatchService reconciliationMatches;
    @Autowired ReconciliationStatusService reconciliationStatuses;
    @Autowired ReconciliationCandidateIgnoreService candidateIgnores;
    @Autowired StatementLineReviewService statementLineReviews;
    @Autowired StatementReconciliationSummaryService statementSummaries;

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
    void confirmedStatementIsPrivateEvidenceAndNeverCreatesCashMovements() {
        byte[] csv = ("data;descricao;valor;direcao\n"
                + "01/03/2026;Pagamento recebido;125,00;ENTRADA\n"
                + "01/03/2026;Pagamento recebido;125,00;ENTRADA\n"
                + "31/02/2026;Linha inválida;10,00;SAIDA\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var result = statementImports.confirm(email, accountId, csv);

        assertThat(result.replay()).isFalse();
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT file_content FROM statement_import WHERE tenant_id = ? AND id = ?", byte[].class, tenantId, result.importId()))
                .containsExactly(csv);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM statement_line WHERE tenant_id = ? AND statement_import_id = ?", Integer.class, tenantId, result.importId()))
                .isEqualTo(3);
        assertThat(statementImports.listImports(email, accountId, 0, 20).total()).isEqualTo(1);
        assertThat(statementImports.listLines(email, result.importId(), 0, 50).items()).hasSize(3);
        assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id = ?", tenantId)).isZero();

        var replay = statementImports.confirm(email, accountId, csv);
        assertThat(replay.replay()).isTrue();
        assertThat(replay.importId()).isEqualTo(result.importId());
        assertThat(count("SELECT count(*) FROM statement_import WHERE tenant_id = ?", tenantId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id = ?", tenantId)).isZero();

        var otherTenantId = UUID.randomUUID();
        var otherUserId = UUID.randomUUID();
        var otherEmail = "other-" + otherTenantId + "@flowpay.local";
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, 'Other tenant')", otherTenantId);
        jdbc.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, 'not-used')", otherUserId, otherEmail);
        jdbc.update("INSERT INTO membership (tenant_id, user_id, role) VALUES (?, ?, 'OWNER')", otherTenantId, otherUserId);
        try {
            assertThatThrownBy(() -> statementImports.listLines(otherEmail, result.importId(), 0, 50))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404 NOT_FOUND");
        } finally {
            jdbc.update("DELETE FROM tenant WHERE id = ?", otherTenantId);
        }
    }

    @Test
    void concurrentConfirmationOfSameStatementCreatesOnlyOneImport() throws Exception {
        byte[] csv = ("data;descricao;valor;direcao\n01/03/2026;Depósito;50,00;ENTRADA\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> confirmTogether(csv, ready, start));
            var second = executor.submit(() -> confirmTogether(csv, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results.stream().map(StatementPreviewService.ConfirmationResult::replay).toList())
                    .containsExactlyInAnyOrder(false, true);
            assertThat(results.get(0).importId()).isEqualTo(results.get(1).importId());
            assertThat(count("SELECT count(*) FROM statement_import WHERE tenant_id = ?", tenantId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM statement_line WHERE tenant_id = ?", tenantId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id = ?", tenantId)).isZero();
        }
    }

    @Test
    void reconciliationSuggestionsRequireExactDirectionAmountAndTenantAndExplainRanking() {
        var movementId = UUID.randomUUID();
        insertManualMovement(tenantId, accountId, categoryId, movementId, LocalDate.of(2026, 3, 1), 12500,
                "ACME contrato pagamento");
        insertManualMovement(tenantId, accountId, categoryId, UUID.randomUUID(), LocalDate.of(2026, 2, 27), 12500,
                "Cliente mensalidade");

        var otherTenantId = UUID.randomUUID();
        var otherAccountId = UUID.randomUUID();
        var otherCategoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, 'Other tenant')", otherTenantId);
        jdbc.update("INSERT INTO financial_category (id, tenant_id, name, direction) VALUES (?, ?, 'Other income', 'INFLOW')", otherCategoryId, otherTenantId);
        jdbc.update("""
                INSERT INTO financial_account (id, tenant_id, name, account_type, opening_balance_date, creation_key, creation_payload_hash)
                VALUES (?, ?, 'Other account', 'BANK', ?, ?, ?)
                """, otherAccountId, otherTenantId, LocalDate.now(), UUID.randomUUID(), "x".repeat(64));
        insertManualMovement(otherTenantId, otherAccountId, otherCategoryId, UUID.randomUUID(), LocalDate.of(2026, 3, 1), 12500,
                "ACME contrato pagamento");

        var importId = UUID.randomUUID();
        var lineId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO statement_import
                  (id, tenant_id, financial_account_id, file_checksum, file_content, total_rows, accepted_rows, rejected_rows, duplicate_rows)
                VALUES (?, ?, ?, ?, ?, 1, 1, 0, 0)
                """, importId, tenantId, accountId, "f".repeat(64), new byte[] {1});
        jdbc.update("""
                INSERT INTO statement_line
                  (id, tenant_id, financial_account_id, statement_import_id, row_number, effective_date, description, direction, amount_minor, row_status)
                VALUES (?, ?, ?, ?, 2, '2026-03-01', 'Cliente ACME contrato', 'INFLOW', 12500, 'ACCEPTED')
                """, lineId, tenantId, accountId, importId);
        try {
            var result = reconciliationSuggestions.suggest(email, lineId);
            assertThat(result.candidateCount()).isEqualTo(2);
            assertThat(result.candidateSetTruncated()).isFalse();
            assertThat(result.suggestions()).hasSize(2);
            assertThat(result.suggestions().getFirst().movement().id()).isEqualTo(movementId);
            assertThat(result.suggestions().getFirst().score()).isGreaterThan(result.suggestions().get(1).score());
            assertThat(result.suggestions().getFirst().evidence()).contains("Mesmo valor", "Mesma direção", "Data igual");
            assertThat(result.explanation()).contains("escolha manual");
        } finally {
            jdbc.update("DELETE FROM cash_transaction WHERE tenant_id = ?", otherTenantId);
            jdbc.update("DELETE FROM financial_account WHERE tenant_id = ?", otherTenantId);
            jdbc.update("DELETE FROM financial_category WHERE tenant_id = ?", otherTenantId);
            jdbc.update("DELETE FROM tenant WHERE id = ?", otherTenantId);
        }
    }

    @Test
    void matchLinksExistingOrCreatesOneMovementAtomicallyAndIdempotently() {
        byte[] csv = ("data;descricao;valor;direcao\n"
                + "01/03/2026;Recebimento conciliado;125,00;ENTRADA\n"
                + "01/03/2026;Outro recebimento;125,00;ENTRADA\n"
                + "02/03/2026;Novo recebimento;250,00;ENTRADA\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var imported = statementImports.confirm(email, accountId, csv);
        var lineIds = jdbc.query("SELECT id FROM statement_line WHERE tenant_id = ? AND statement_import_id = ? ORDER BY row_number",
                (rs, row) -> rs.getObject("id", UUID.class), tenantId, imported.importId());
        var existingMovementId = UUID.randomUUID();
        insertManualMovement(tenantId, accountId, categoryId, existingMovementId, LocalDate.of(2026, 3, 1), 12500, "Recebimento conciliado");

        var linkKey = UUID.randomUUID();
        var linkRequest = new ReconciliationMatchService.MatchRequest(ReconciliationMatchService.Decision.LINK_EXISTING, existingMovementId, null);
        var linked = reconciliationMatches.match(email, lineIds.get(0), linkKey, linkRequest);
        var replay = reconciliationMatches.match(email, lineIds.get(0), linkKey, linkRequest);
        assertThat(linked.active()).isTrue();
        assertThat(linked.replay()).isFalse();
        assertThat(replay.replay()).isTrue();
        assertThat(replay.cashTransactionId()).isEqualTo(existingMovementId);
        assertThat(reconciliationStatuses.activeMatches(email, java.util.List.of(lineIds.get(0))))
                .singleElement().satisfies(status -> assertThat(status.matchId()).isEqualTo(linked.matchId()));
        assertThatThrownBy(() -> reconciliationSuggestions.suggest(email, lineIds.get(0)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
        assertThatThrownBy(() -> reconciliationMatches.match(email, lineIds.get(1), UUID.randomUUID(), linkRequest))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");

        var createRequest = new ReconciliationMatchService.MatchRequest(ReconciliationMatchService.Decision.CREATE_MOVEMENT, null, categoryId);
        var createKey = UUID.randomUUID();
        var created = reconciliationMatches.match(email, lineIds.get(2), createKey, createRequest);
        var createReplay = reconciliationMatches.match(email, lineIds.get(2), createKey, createRequest);
        assertThat(created.decision()).isEqualTo("CREATE_MOVEMENT");
        assertThat(createReplay.replay()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_match WHERE tenant_id = ? AND reversed_at IS NULL", Integer.class, tenantId))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id = ?", tenantId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount_minor::text FROM cash_transaction WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, created.cashTransactionId())).isEqualTo("25000");
    }

    @Test
    void lineReviewIsAuditedIdempotentAndIgnoredRowsCannotBeMatched() {
        byte[] csv = ("data;descricao;valor;direcao\n01/03/2026;Revisar recebimento;125,00;ENTRADA\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var imported = statementImports.confirm(email, accountId, csv);
        var lineId = jdbc.queryForObject("SELECT id FROM statement_line WHERE tenant_id=? AND statement_import_id=?",
                UUID.class, tenantId, imported.importId());
        var reviewKey = UUID.randomUUID();
        var request = new StatementLineReviewService.ReviewRequest(StatementLineReviewService.Action.IGNORE_LINE, "Não reconheço o lançamento");

        var ignored = statementLineReviews.review(email, lineId, reviewKey, request);
        var replay = statementLineReviews.review(email, lineId, reviewKey, request);
        assertThat(ignored.reviewStatus()).isEqualTo("IGNORED");
        assertThat(replay.replay()).isTrue();
        assertThat(count("SELECT count(*) FROM statement_line_review_event WHERE tenant_id=?", tenantId)).isEqualTo(1);
        assertThatThrownBy(() -> reconciliationSuggestions.suggest(email, lineId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");

        statementLineReviews.review(email, lineId, UUID.randomUUID(),
                new StatementLineReviewService.ReviewRequest(StatementLineReviewService.Action.REOPEN, "Revisão solicitada"));
        assertThat(jdbc.queryForObject("SELECT review_status FROM statement_line WHERE tenant_id=? AND id=?", String.class, tenantId, lineId))
                .isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id=?", tenantId)).isZero();
    }

    @Test
    void candidateIgnoreCanBeRestoredWithoutChangingCash() {
        byte[] csv = ("data;descricao;valor;direcao\n01/03/2026;Recebimento semelhante;125,00;ENTRADA\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var imported = statementImports.confirm(email, accountId, csv);
        var lineId = jdbc.queryForObject("SELECT id FROM statement_line WHERE tenant_id=? AND statement_import_id=?",
                UUID.class, tenantId, imported.importId());
        var movement1 = UUID.randomUUID();
        var movement2 = UUID.randomUUID();
        insertManualMovement(tenantId, accountId, categoryId, movement1, LocalDate.of(2026, 3, 1), 12500, "Recebimento semelhante A");
        insertManualMovement(tenantId, accountId, categoryId, movement2, LocalDate.of(2026, 3, 1), 12500, "Recebimento semelhante B");

        var before = count("SELECT count(*) FROM cash_transaction WHERE tenant_id=?", tenantId);
        var ignoreKey = UUID.randomUUID();
        var ignoreRequest = new ReconciliationCandidateIgnoreService.CandidateRequest("Operação já revisada em outro contexto");
        var ignored = candidateIgnores.ignore(email, lineId, movement1, ignoreKey, ignoreRequest);
        assertThat(candidateIgnores.ignore(email, lineId, movement1, ignoreKey, ignoreRequest).replay()).isTrue();
        assertThatThrownBy(() -> candidateIgnores.ignore(email, lineId, movement1, ignoreKey,
                new ReconciliationCandidateIgnoreService.CandidateRequest("Motivo diferente")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
        var suggestions = reconciliationSuggestions.suggest(email, lineId);
        assertThat(suggestions.suggestions()).extracting(item -> item.movement().id()).contains(movement2).doesNotContain(movement1);
        assertThat(suggestions.ignoredCandidates()).singleElement().satisfies(item -> {
            assertThat(item.movement().id()).isEqualTo(movement1);
            assertThat(item.reason()).contains("já revisada");
        });

        var restoreKey = UUID.randomUUID();
        var restoreRequest = new ReconciliationCandidateIgnoreService.CandidateRequest("Reavaliar com evidência nova");
        candidateIgnores.restore(email, lineId, movement1, restoreKey, restoreRequest);
        assertThat(candidateIgnores.restore(email, lineId, movement1, restoreKey, restoreRequest).replay()).isTrue();
        assertThat(reconciliationSuggestions.suggest(email, lineId).suggestions())
                .extracting(item -> item.movement().id()).contains(movement1, movement2);
        assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id=?", tenantId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT reason FROM reconciliation_candidate_suppression WHERE tenant_id=? AND id=?",
                String.class, tenantId, ignored.suppressionId())).isEqualTo("Operação já revisada em outro contexto");
    }

    @Test
    void unlinkPreservesMovementAndRecordsReason() {
        byte[] csv = ("data;descricao;valor;direcao\n01/03/2026;Novo recebimento;125,00;ENTRADA\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var imported = statementImports.confirm(email, accountId, csv);
        var lineId = jdbc.queryForObject("SELECT id FROM statement_line WHERE tenant_id=? AND statement_import_id=?",
                UUID.class, tenantId, imported.importId());
        var match = reconciliationMatches.match(email, lineId, UUID.randomUUID(),
                new ReconciliationMatchService.MatchRequest(ReconciliationMatchService.Decision.CREATE_MOVEMENT, null, categoryId));
        long before = count("SELECT count(*) FROM cash_transaction WHERE tenant_id=?", tenantId);

        var unlinkKey = UUID.randomUUID();
        var unlinkRequest = new ReconciliationMatchService.UnlinkRequest("A linha foi associada à categoria errada");
        var unlinked = reconciliationMatches.unlink(email, lineId, unlinkKey, unlinkRequest);
        assertThat(reconciliationMatches.unlink(email, lineId, unlinkKey, unlinkRequest).replay()).isTrue();

        assertThat(unlinked.cashMovementPreserved()).isTrue();
        assertThat(count("SELECT count(*) FROM cash_transaction WHERE tenant_id=?", tenantId)).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM reconciliation_match WHERE tenant_id=? AND id=? AND reversed_at IS NOT NULL", tenantId, match.matchId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reversal_reason FROM reconciliation_match WHERE tenant_id=? AND id=?", String.class, tenantId, match.matchId()))
                .isEqualTo("A linha foi associada à categoria errada");
    }

    @Test
    void statementSummaryComparesAccountActivityAndUsesExplicitBalanceReference() {
        var today = LocalDate.of(2026, 3, 1);
        var outflowCategoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO financial_category (id, tenant_id, name, direction) VALUES (?, ?, 'Despesa teste', 'OUTFLOW')",
                outflowCategoryId, tenantId);
        jdbc.update("""
                INSERT INTO cash_transaction (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description)
                VALUES (?, ?, ?, 'INFLOW', 50000, '2026-02-01', 'OPENING_BALANCE', 'Saldo inicial')
                """, UUID.randomUUID(), tenantId, accountId);
        var recordedInflow = UUID.randomUUID();
        insertManualMovement(tenantId, accountId, categoryId, recordedInflow, today, 12500, "Entrada registrada");
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description,
                   category_id, category_direction, idempotency_key, payload_hash)
                VALUES (?, ?, ?, 'OUTFLOW', 3000, ?, 'MANUAL', 'Saída registrada', ?, 'OUTFLOW', ?, ?)
                """, UUID.randomUUID(), tenantId, accountId, today, outflowCategoryId, UUID.randomUUID(), "j".repeat(64));

        byte[] csv = ("data;descricao;valor;direcao\n01/03/2026;Entrada registrada;125,00;ENTRADA\n"
                + "01/03/2026;Saída no extrato;20,00;SAIDA\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var imported = statementImports.confirm(email, accountId, csv);
        var lines = jdbc.query("SELECT id FROM statement_line WHERE tenant_id=? AND statement_import_id=? ORDER BY row_number",
                (rs, row) -> rs.getObject(1, UUID.class), tenantId, imported.importId());
        reconciliationMatches.match(email, lines.get(0), UUID.randomUUID(),
                new ReconciliationMatchService.MatchRequest(ReconciliationMatchService.Decision.LINK_EXISTING, recordedInflow, null));

        var summary = statementSummaries.get(email, imported.importId(), "America/Sao_Paulo");
        assertThat(summary.periodFrom()).isEqualTo(today);
        assertThat(summary.periodTo()).isEqualTo(today);
        assertThat(summary.asOf()).isEqualTo(today);
        assertThat(summary.acceptedCount()).isEqualTo(2);
        assertThat(summary.matchedCount()).isEqualTo(1);
        assertThat(summary.statementInflowsMinor()).isEqualTo("12500");
        assertThat(summary.statementOutflowsMinor()).isEqualTo("2000");
        assertThat(summary.controlledInflowsMinor()).isEqualTo("12500");
        assertThat(summary.controlledOutflowsMinor()).isEqualTo("3000");
        assertThat(summary.inflowActivityDifferenceMinor()).isEqualTo("0");
        assertThat(summary.outflowActivityDifferenceMinor()).isEqualTo("-1000");
        assertThat(summary.netActivityDifferenceMinor()).isEqualTo("1000");
        assertThat(summary.controlledBalanceMinor()).isEqualTo("59500");
        assertThat(summary.statementBalanceAvailable()).isFalse();
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

            var monthly = monthlyReports.get(email, YearMonth.from(today), "America/Sao_Paulo", 0, 20);
            assertThat(monthly.periodInflowsMinor()).isEqualTo("7000");
            assertThat(monthly.receivablesDueMinor()).isEqualTo("3000");
            assertThat(monthly.movements()).hasSize(2);
            assertThat(monthly.categories()).singleElement().satisfies(category -> {
                assertThat(category.categoryId()).isEqualTo(categoryId);
                assertThat(category.direction()).isEqualTo("INFLOW");
                assertThat(category.amountMinor()).isEqualTo("7000");
            });
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

    private StatementPreviewService.ConfirmationResult confirmTogether(byte[] csv, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent import start timed out");
        return statementImports.confirm(email, accountId, csv);
    }

    private void insertManualMovement(UUID movementTenantId, UUID movementAccountId, UUID movementCategoryId,
                                      UUID movementId, LocalDate date, long amountMinor, String description) {
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description,
                   category_id, category_direction, idempotency_key, payload_hash)
                VALUES (?, ?, ?, 'INFLOW', ?, ?, 'MANUAL', ?, ?, 'INFLOW', ?, ?)
                """, movementId, movementTenantId, movementAccountId, amountMinor, date, description,
                movementCategoryId, UUID.randomUUID(), "h".repeat(64));
    }

    private long count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }

    private long count(String sql, UUID firstId, UUID secondId) {
        return jdbc.queryForObject(sql, Long.class, firstId, secondId);
    }
}
