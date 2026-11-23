package com.flowpay.reconciliation.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowpay.finance.api.FinanceReconciliationReader;
import com.flowpay.identity.api.TenantAccess;
import com.flowpay.statements.api.StatementLineReader;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReconciliationCandidateIgnoreServiceTest {
    @Mock TenantAccess tenants;
    @Mock StatementLineReader lines;
    @Mock FinanceReconciliationReader finance;
    @Mock JdbcTemplate jdbc;

    @Test
    void readerCannotSuppressCandidate() {
        when(tenants.requireSingleTenant("reader@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(UUID.randomUUID(), "Empresa", "READER"));
        var service = new ReconciliationCandidateIgnoreService(tenants, lines, finance, jdbc);

        assertThatThrownBy(() -> service.ignore("reader@example.test", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ReconciliationCandidateIgnoreService.CandidateRequest("Duplicada")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(lines, finance, jdbc);
    }

    @Test
    void requiresReasonForSuppressionAndRestore() {
        when(tenants.requireSingleTenant("owner@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(UUID.randomUUID(), "Empresa", "OWNER"));
        var service = new ReconciliationCandidateIgnoreService(tenants, lines, finance, jdbc);

        assertThatThrownBy(() -> service.restore("owner@example.test", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new ReconciliationCandidateIgnoreService.CandidateRequest("  ")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(lines, finance, jdbc);
    }
}
