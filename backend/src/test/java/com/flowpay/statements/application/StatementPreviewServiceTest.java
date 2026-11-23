package com.flowpay.statements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowpay.finance.api.FinanceAccountReader;
import com.flowpay.identity.api.TenantAccess;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StatementPreviewServiceTest {
    @Mock TenantAccess tenantAccess;
    @Mock FinanceAccountReader accounts;
    @Mock JdbcTemplate jdbc;

    @Test
    void derivesTenantFromAuthenticatedUserAndPreviewsAuthorizedAccount() {
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        when(tenantAccess.requireSingleTenant("owner@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(tenantId, "Empresa", "OWNER"));
        when(accounts.isActiveAccount(tenantId, accountId)).thenReturn(true);
        var service = new StatementPreviewService(tenantAccess, accounts, jdbc);

        var preview = service.preview("owner@example.test", accountId,
                "data;descricao;valor;direcao\n01/03/2026;Serviço;12,00;ENTRADA\n".getBytes(StandardCharsets.UTF_8));

        assertThat(preview.acceptedCount()).isEqualTo(1);
        verify(tenantAccess).requireSingleTenant("owner@example.test");
        verify(accounts).isActiveAccount(tenantId, accountId);
    }

    @Test
    void rejectsInactiveOrForeignAccountBeforeParsingAnyFileContent() {
        var tenantId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        when(tenantAccess.requireSingleTenant("user@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(tenantId, "Empresa", "OWNER"));
        when(accounts.isActiveAccount(tenantId, accountId)).thenReturn(false);
        var service = new StatementPreviewService(tenantAccess, accounts, jdbc);

        assertThatThrownBy(() -> service.preview("user@example.test", accountId, new byte[] {0}))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
        verify(accounts).isActiveAccount(tenantId, accountId);
    }

    @Test
    void readerCannotPreviewOrImportStatements() {
        when(tenantAccess.requireSingleTenant("reader@example.test"))
                .thenReturn(new TenantAccess.CurrentTenant(UUID.randomUUID(), "Empresa", "READER"));
        var service = new StatementPreviewService(tenantAccess, accounts, jdbc);

        assertThatThrownBy(() -> service.preview("reader@example.test", UUID.randomUUID(), new byte[0]))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403 FORBIDDEN");
        org.mockito.Mockito.verifyNoInteractions(accounts, jdbc);
    }
}
