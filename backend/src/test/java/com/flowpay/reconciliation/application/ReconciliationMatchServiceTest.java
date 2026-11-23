package com.flowpay.reconciliation.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowpay.finance.api.FinanceReconciliationReader;
import com.flowpay.finance.api.FinanceReconciliationWriter;
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
class ReconciliationMatchServiceTest {
    @Mock TenantAccess tenantAccess;
    @Mock FinanceReconciliationReader finance;
    @Mock FinanceReconciliationWriter financeWriter;
    @Mock StatementLineReader statementLines;
    @Mock JdbcTemplate jdbc;

    @Test
    void readerCannotCommitAReconciliation() {
        var tenantId = UUID.randomUUID();
        when(tenantAccess.requireSingleTenant("reader@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(tenantId, "Empresa", "READER"));
        var service = new ReconciliationMatchService(tenantAccess, finance, financeWriter, statementLines, jdbc);

        assertThatThrownBy(() -> service.match("reader@example.test", UUID.randomUUID(), UUID.randomUUID(),
                new ReconciliationMatchService.MatchRequest(ReconciliationMatchService.Decision.CREATE_MOVEMENT, null, UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(finance, financeWriter, statementLines, jdbc);
    }

    @Test
    void requiresExactlyTheFieldsForTheSelectedPath() {
        when(tenantAccess.requireSingleTenant("owner@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(UUID.randomUUID(), "Empresa", "OWNER"));
        var service = new ReconciliationMatchService(tenantAccess, finance, financeWriter, statementLines, jdbc);

        assertThatThrownBy(() -> service.match("owner@example.test", UUID.randomUUID(), UUID.randomUUID(),
                new ReconciliationMatchService.MatchRequest(ReconciliationMatchService.Decision.LINK_EXISTING, null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(finance, financeWriter, statementLines, jdbc);
    }
}
