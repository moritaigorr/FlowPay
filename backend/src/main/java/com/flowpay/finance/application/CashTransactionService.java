package com.flowpay.finance.application;

import com.flowpay.identity.api.TenantAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
public class CashTransactionService {
    private static final long MAX_AMOUNT_MINOR = 9_000_000_000_000_000L;
    private final JdbcTemplate jdbc;
    private final TenantAccess tenantAccess;

    public CashTransactionService(JdbcTemplate jdbc, TenantAccess tenantAccess) {
        this.jdbc = jdbc;
        this.tenantAccess = tenantAccess;
    }

    @Transactional(readOnly = true)
    public TransactionPage list(String email, int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho fora dos limites permitidos.");
        }
        var tenant = tenantAccess.requireSingleTenant(email);
        var total = jdbc.queryForObject("SELECT count(*) FROM cash_transaction WHERE tenant_id = ?", Long.class, tenant.id());
        var items = jdbc.query("""
                SELECT ct.id, ct.direction, ct.amount_minor::text AS amount_minor, ct.effective_date,
                       ct.origin, ct.description, ct.category_id, cat.name AS category_name,
                       ct.financial_account_id, a.name AS account_name
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                LEFT JOIN financial_category cat ON cat.tenant_id = ct.tenant_id AND cat.id = ct.category_id
                WHERE ct.tenant_id = ?
                ORDER BY ct.effective_date DESC, ct.created_at DESC, ct.id DESC
                LIMIT ? OFFSET ?
                """, CashTransactionService::map, tenant.id(), size, page * size);
        return new TransactionPage(items, total, page, size);
    }

    @Transactional
    public CreateResult create(String email, UUID key, TransactionInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        if (!"OWNER".equals(tenant.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite criar movimentos.");
        if (input.amountMinor() <= 0 || input.amountMinor() > MAX_AMOUNT_MINOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor precisa ser positivo e estar dentro do limite permitido.");
        }
        var description = input.description().trim();
        var hash = payloadHash(input.direction(), input.accountId(), input.categoryId(), input.amountMinor(), input.effectiveDate(), description);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { rs.next(); return null; },
                tenant.id() + ":MANUAL_CASH_TRANSACTION:" + key);
        var prior = findByKey(tenant.id(), key);
        if (prior != null) {
            if (!sameHash(prior.hash(), hash)) throw idempotencyConflict();
            return new CreateResult(get(tenant.id(), prior.id()), true);
        }
        var accountStatus = jdbc.query("SELECT status FROM financial_account WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> rs.getString("status"), tenant.id(), input.accountId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma conta desta empresa."));
        if (!"ACTIVE".equals(accountStatus)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A conta selecionada está arquivada.");
        var categoryValid = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM financial_category WHERE tenant_id = ? AND id = ? AND direction = ? AND status = 'ACTIVE')
                """, Boolean.class, tenant.id(), input.categoryId(), input.direction().name()));
        if (!categoryValid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma categoria ativa da direção correta nesta empresa.");
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description,
                   category_id, category_direction, idempotency_key, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', ?, ?, ?, ?, ?)
                """, id, tenant.id(), input.accountId(), input.direction().name(), input.amountMinor(), input.effectiveDate(),
                description, input.categoryId(), input.direction().name(), key, hash);
        return new CreateResult(get(tenant.id(), id), false);
    }

    private Existing findByKey(UUID tenantId, UUID key) {
        return jdbc.query("SELECT id, payload_hash FROM cash_transaction WHERE tenant_id = ? AND origin = 'MANUAL' AND idempotency_key = ?",
                (rs, row) -> new Existing(rs.getObject("id", UUID.class), rs.getString("payload_hash")), tenantId, key)
                .stream().findFirst().orElse(null);
    }

    private TransactionView get(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT ct.id, ct.direction, ct.amount_minor::text AS amount_minor, ct.effective_date,
                       ct.origin, ct.description, ct.category_id, cat.name AS category_name,
                       ct.financial_account_id, a.name AS account_name
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                LEFT JOIN financial_category cat ON cat.tenant_id = ct.tenant_id AND cat.id = ct.category_id
                WHERE ct.tenant_id = ? AND ct.id = ?
                """, CashTransactionService::map, tenantId, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimento não encontrado."));
    }

    private static TransactionView map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new TransactionView(rs.getObject("id", UUID.class), Direction.valueOf(rs.getString("direction")),
                rs.getString("amount_minor"), rs.getDate("effective_date").toLocalDate(), rs.getString("origin"),
                rs.getString("description"), rs.getObject("category_id", UUID.class), rs.getString("category_name"),
                rs.getObject("financial_account_id", UUID.class), rs.getString("account_name"));
    }

    private static String payloadHash(Direction direction, UUID accountId, UUID categoryId, long amountMinor, LocalDate date, String description) {
        var canonical = String.join("\u0000", direction.name(), accountId.toString(), categoryId.toString(),
                Long.toString(amountMinor), date.toString(), description);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private static boolean sameHash(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private static ResponseStatusException idempotencyConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A chave de idempotência já foi usada com outros dados.");
    }

    public enum Direction { INFLOW, OUTFLOW }
    public record TransactionInput(Direction direction, UUID accountId, UUID categoryId, long amountMinor, LocalDate effectiveDate, String description) {}
    public record TransactionView(UUID id, Direction direction, String amountMinor, LocalDate effectiveDate, String origin,
                                  String description, UUID categoryId, String categoryName, UUID accountId, String accountName) {}
    public record TransactionPage(List<TransactionView> items, long total, int page, int size) {}
    public record CreateResult(TransactionView transaction, boolean replay) {}
    private record Existing(UUID id, String hash) {}
}
