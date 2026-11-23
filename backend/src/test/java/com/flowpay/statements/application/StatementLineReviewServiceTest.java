package com.flowpay.statements.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
class StatementLineReviewServiceTest {
    @Mock TenantAccess tenants;
    @Mock StatementLineReader lines;
    @Mock JdbcTemplate jdbc;

    @Test
    void readerCannotChangeReviewState() {
        when(tenants.requireSingleTenant("reader@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(UUID.randomUUID(), "Empresa", "READER"));
        var service = new StatementLineReviewService(tenants, lines, jdbc);

        assertThatThrownBy(() -> service.review("reader@example.test", UUID.randomUUID(), UUID.randomUUID(),
                new StatementLineReviewService.ReviewRequest(StatementLineReviewService.Action.IGNORE_LINE, "Duplicada")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(lines, jdbc);
    }

    @Test
    void requiresAnAuditableReasonBeforeDatabaseAccess() {
        when(tenants.requireSingleTenant("owner@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(UUID.randomUUID(), "Empresa", "OWNER"));
        var service = new StatementLineReviewService(tenants, lines, jdbc);

        assertThatThrownBy(() -> service.review("owner@example.test", UUID.randomUUID(), UUID.randomUUID(),
                new StatementLineReviewService.ReviewRequest(StatementLineReviewService.Action.REOPEN, "  ")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(lines, jdbc);
    }
}
