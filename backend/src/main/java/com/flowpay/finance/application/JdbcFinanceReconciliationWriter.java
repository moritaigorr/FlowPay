package com.flowpay.finance.application;

import com.flowpay.finance.api.FinanceReconciliationReader;
import com.flowpay.finance.api.FinanceReconciliationWriter;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JdbcFinanceReconciliationWriter implements FinanceReconciliationWriter {
    private final JdbcTemplate jdbc;

    public JdbcFinanceReconciliationWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public FinanceReconciliationReader.MovementCandidate createFromStatement(UUID tenantId, UUID accountId, String direction,
                                                                              long amountMinor, LocalDate effectiveDate,
                                                                              String description, UUID categoryId) {
        var accountExists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM financial_account WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE')
                """, Boolean.class, tenantId, accountId);
        if (!Boolean.TRUE.equals(accountExists)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta financeira não encontrada.");
        var categoryMatches = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM financial_category WHERE tenant_id = ? AND id = ? AND direction = ? AND status = 'ACTIVE')
                """, Boolean.class, tenantId, categoryId, direction);
        if (!Boolean.TRUE.equals(categoryMatches)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma categoria ativa da mesma direção.");
        if (description == null || description.isBlank() || description.length() > 160)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A descrição precisa ter de 1 a 160 caracteres para criar o movimento.");

        var movementId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, currency, effective_date, origin,
                   description, category_id, category_direction)
                VALUES (?, ?, ?, ?, ?, 'BRL', ?, 'STATEMENT_RECONCILIATION', ?, ?, ?)
                """, movementId, tenantId, accountId, direction, amountMinor, effectiveDate,
                description, categoryId, direction);
        return jdbc.queryForObject("""
                SELECT ct.id, ct.financial_account_id, a.name AS account_name, ct.direction,
                       ct.amount_minor::text AS amount_minor, ct.currency, ct.effective_date, ct.origin, ct.description
                FROM cash_transaction ct JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                WHERE ct.tenant_id = ? AND ct.id = ?
                """, (rs, row) -> new FinanceReconciliationReader.MovementCandidate(rs.getObject("id", UUID.class),
                rs.getObject("financial_account_id", UUID.class), rs.getString("account_name"),
                rs.getString("direction"), rs.getString("amount_minor"), rs.getString("currency"),
                rs.getDate("effective_date").toLocalDate(), rs.getString("origin"), rs.getString("description")), tenantId, movementId);
    }
}
