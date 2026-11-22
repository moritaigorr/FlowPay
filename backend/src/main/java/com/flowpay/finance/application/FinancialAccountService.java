package com.flowpay.finance.application;

import com.flowpay.identity.api.TenantAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialAccountService {
    private final JdbcTemplate jdbc;
    private final TenantAccess tenantAccess;

    public FinancialAccountService(JdbcTemplate jdbc, TenantAccess tenantAccess) {
        this.jdbc = jdbc;
        this.tenantAccess = tenantAccess;
    }

    @Transactional(readOnly = true)
    public AccountPage list(String email, int page, int size) {
        validatePage(page, size);
        var tenant = tenantAccess.requireSingleTenant(email);
        var total = jdbc.queryForObject("SELECT count(*) FROM financial_account WHERE tenant_id = ?", Long.class, tenant.id());
        var accounts = jdbc.query(accountSelect() + " WHERE a.tenant_id = ?" + accountGroupBy() + " ORDER BY lower(a.name), a.id LIMIT ? OFFSET ?",
                FinancialAccountService::mapAccount, tenant.id(), size, page * size);
        return new AccountPage(accounts, total, page, size);
    }

    @Transactional(readOnly = true)
    public AccountView get(String email, UUID id) {
        var tenant = tenantAccess.requireSingleTenant(email);
        return get(tenant.id(), id);
    }

    @Transactional
    public CreateResult create(String email, UUID key, AccountInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireOwner(tenant.role());
        var name = input.name().trim();
        var hash = payloadHash(name, input.type(), input.openingBalanceMinor(), input.openingBalanceDate());
        var id = UUID.randomUUID();
        var inserted = jdbc.update("""
                INSERT INTO financial_account (id, tenant_id, name, account_type, opening_balance_date, creation_key, creation_payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, creation_key) DO NOTHING
                """, id, tenant.id(), name, input.type().name(), input.openingBalanceDate(), key, hash);

        if (inserted == 0) {
            var existing = jdbc.query("""
                    SELECT id, creation_payload_hash FROM financial_account WHERE tenant_id = ? AND creation_key = ?
                    """, (rs, row) -> new ExistingCreation(rs.getObject("id", UUID.class), rs.getString("creation_payload_hash")), tenant.id(), key)
                    .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Chave de criação em conflito."));
            if (!MessageDigest.isEqual(existing.payloadHash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A chave de idempotência já foi usada com outros dados.");
            }
            return new CreateResult(get(tenant.id(), existing.id()), true);
        }

        var balance = input.openingBalanceMinor();
        if (balance != 0) {
            jdbc.update("""
                    INSERT INTO cash_transaction
                      (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description)
                    VALUES (?, ?, ?, ?, ?, ?, 'OPENING_BALANCE', 'Saldo inicial')
                    """, UUID.randomUUID(), tenant.id(), id, balance > 0 ? "INFLOW" : "OUTFLOW",
                    Math.abs(balance), input.openingBalanceDate());
        }
        return new CreateResult(get(tenant.id(), id), false);
    }

    @Transactional
    public AccountView update(String email, UUID id, String name, AccountStatus status) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireOwner(tenant.role());
        var changed = jdbc.update("""
                UPDATE financial_account SET name = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ?
                """, name.trim(), status.name(), tenant.id(), id);
        if (changed == 0) throw notFound();
        return get(tenant.id(), id);
    }

    private AccountView get(UUID tenantId, UUID id) {
        return jdbc.query(accountSelect() + " WHERE a.tenant_id = ? AND a.id = ?" + accountGroupBy(),
                FinancialAccountService::mapAccount, tenantId, id).stream().findFirst().orElseThrow(FinancialAccountService::notFound);
    }

    private static String accountSelect() {
        return """
                SELECT a.id, a.name, a.account_type, a.status, a.currency,
                       COALESCE(SUM(CASE WHEN ct.direction = 'INFLOW' THEN ct.amount_minor ELSE -ct.amount_minor END), 0)::text AS balance_minor,
                       a.opening_balance_date,
                       a.created_at, a.updated_at
                FROM financial_account a
                LEFT JOIN cash_transaction ct ON ct.tenant_id = a.tenant_id AND ct.financial_account_id = a.id
                """;
    }

    private static String accountGroupBy() { return " GROUP BY a.tenant_id, a.id"; }

    private static AccountView mapAccount(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var openingDate = rs.getDate("opening_balance_date");
        return new AccountView(rs.getObject("id", UUID.class), rs.getString("name"),
                AccountType.valueOf(rs.getString("account_type")), AccountStatus.valueOf(rs.getString("status")),
                rs.getString("currency"), rs.getString("balance_minor"),
                openingDate == null ? null : openingDate.toLocalDate(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static String payloadHash(String name, AccountType type, long balance, LocalDate date) {
        var canonical = name + "\u0000" + type.name() + "\u0000" + balance + "\u0000" + date;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho fora dos limites permitidos.");
        }
    }

    private static void requireOwner(String role) {
        if (!"OWNER".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite alterar contas financeiras.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta financeira não encontrada.");
    }

    private record ExistingCreation(UUID id, String payloadHash) {}
    public enum AccountType { BANK, CASH, OTHER }
    public enum AccountStatus { ACTIVE, ARCHIVED }
    public record AccountInput(String name, AccountType type, long openingBalanceMinor, LocalDate openingBalanceDate) {}
    public record AccountView(UUID id, String name, AccountType type, AccountStatus status, String currency,
                              String balanceMinor, LocalDate openingBalanceDate, Instant createdAt, Instant updatedAt) {
        @Override public String toString() { return "AccountView[REDACTED]"; }
    }
    public record AccountPage(List<AccountView> items, long total, int page, int size) {}
    public record CreateResult(AccountView account, boolean replay) {}
}
