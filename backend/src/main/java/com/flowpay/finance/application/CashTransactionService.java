package com.flowpay.finance.application;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.identity.api.TenantRole;
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
                       ct.financial_account_id, a.name AS account_name, counterparty.name AS counterparty_account_name
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                LEFT JOIN financial_category cat ON cat.tenant_id = ct.tenant_id AND cat.id = ct.category_id
                LEFT JOIN cash_transfer tr ON tr.tenant_id = ct.tenant_id AND tr.id = ct.cash_transfer_id
                LEFT JOIN financial_account counterparty ON counterparty.tenant_id = tr.tenant_id
                    AND counterparty.id = CASE WHEN ct.direction = 'OUTFLOW' THEN tr.destination_account_id ELSE tr.source_account_id END
                WHERE ct.tenant_id = ?
                ORDER BY ct.effective_date DESC, ct.created_at DESC, ct.id DESC
                LIMIT ? OFFSET ?
                """, CashTransactionService::map, tenant.id(), size, page * size);
        return new TransactionPage(items, total, page, size);
    }

    @Transactional
    public CreateResult create(String email, UUID key, TransactionInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        if (!TenantRole.canManageBusinessData(tenant.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite criar movimentos.");
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

    @Transactional
    public TransferResult transfer(String email, UUID key, TransferInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        if (!TenantRole.canManageBusinessData(tenant.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite criar movimentos.");
        if (input.sourceAccountId().equals(input.destinationAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha contas diferentes para a transferência.");
        }
        if (input.amountMinor() <= 0 || input.amountMinor() > MAX_AMOUNT_MINOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor precisa ser positivo e estar dentro do limite permitido.");
        }
        var description = input.description().trim();
        var hash = transferPayloadHash(input, description);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { rs.next(); return null; },
                tenant.id() + ":CASH_TRANSFER:" + key);
        var prior = jdbc.query("SELECT id, payload_hash FROM cash_transfer WHERE tenant_id = ? AND idempotency_key = ?",
                (rs, row) -> new Existing(rs.getObject("id", UUID.class), rs.getString("payload_hash")), tenant.id(), key)
                .stream().findFirst().orElse(null);
        if (prior != null) {
            if (!sameHash(prior.hash(), hash)) throw idempotencyConflict();
            return new TransferResult(getTransferLeg(tenant.id(), prior.id(), "OUTFLOW"),
                    getTransferLeg(tenant.id(), prior.id(), "INFLOW"), true);
        }

        var accounts = jdbc.query("""
                        SELECT id, status FROM financial_account
                        WHERE tenant_id = ? AND id IN (?, ?)
                        ORDER BY id FOR UPDATE
                        """, (rs, row) -> new AccountState(rs.getObject("id", UUID.class), rs.getString("status")),
                tenant.id(), input.sourceAccountId(), input.destinationAccountId());
        if (accounts.size() != 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione duas contas desta empresa.");
        if (accounts.stream().anyMatch(account -> !"ACTIVE".equals(account.status()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "As contas selecionadas precisam estar ativas.");
        }

        var transferId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_transfer
                  (id, tenant_id, source_account_id, destination_account_id, amount_minor, effective_date,
                   description, idempotency_key, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, transferId, tenant.id(), input.sourceAccountId(), input.destinationAccountId(),
                input.amountMinor(), input.effectiveDate(), description, key, hash);
        insertTransferLeg(tenant.id(), transferId, input.sourceAccountId(), "OUTFLOW", input, description);
        insertTransferLeg(tenant.id(), transferId, input.destinationAccountId(), "INFLOW", input, description);
        return new TransferResult(getTransferLeg(tenant.id(), transferId, "OUTFLOW"),
                getTransferLeg(tenant.id(), transferId, "INFLOW"), false);
    }

    private void insertTransferLeg(UUID tenantId, UUID transferId, UUID accountId, String direction,
                                   TransferInput input, String description) {
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin,
                   description, cash_transfer_id)
                VALUES (?, ?, ?, ?, ?, ?, 'TRANSFER', ?, ?)
                """, UUID.randomUUID(), tenantId, accountId, direction, input.amountMinor(),
                input.effectiveDate(), description, transferId);
    }

    private TransactionView getTransferLeg(UUID tenantId, UUID transferId, String direction) {
        return jdbc.query("""
                SELECT ct.id, ct.direction, ct.amount_minor::text AS amount_minor, ct.effective_date,
                       ct.origin, ct.description, ct.category_id, cat.name AS category_name,
                       ct.financial_account_id, a.name AS account_name, counterparty.name AS counterparty_account_name
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                LEFT JOIN financial_category cat ON cat.tenant_id = ct.tenant_id AND cat.id = ct.category_id
                LEFT JOIN cash_transfer tr ON tr.tenant_id = ct.tenant_id AND tr.id = ct.cash_transfer_id
                LEFT JOIN financial_account counterparty ON counterparty.tenant_id = tr.tenant_id
                    AND counterparty.id = CASE WHEN ct.direction = 'OUTFLOW' THEN tr.destination_account_id ELSE tr.source_account_id END
                WHERE ct.tenant_id = ? AND ct.cash_transfer_id = ? AND ct.direction = ?
                """, CashTransactionService::map, tenantId, transferId, direction).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "A transferência não tem as duas pernas esperadas."));
    }

    private static String transferPayloadHash(TransferInput input, String description) {
        var canonical = String.join("\u0000", input.sourceAccountId().toString(), input.destinationAccountId().toString(),
                Long.toString(input.amountMinor()), input.effectiveDate().toString(), description);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
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
                       ct.financial_account_id, a.name AS account_name, counterparty.name AS counterparty_account_name
                FROM cash_transaction ct
                JOIN financial_account a ON a.tenant_id = ct.tenant_id AND a.id = ct.financial_account_id
                LEFT JOIN financial_category cat ON cat.tenant_id = ct.tenant_id AND cat.id = ct.category_id
                LEFT JOIN cash_transfer tr ON tr.tenant_id = ct.tenant_id AND tr.id = ct.cash_transfer_id
                LEFT JOIN financial_account counterparty ON counterparty.tenant_id = tr.tenant_id
                    AND counterparty.id = CASE WHEN ct.direction = 'OUTFLOW' THEN tr.destination_account_id ELSE tr.source_account_id END
                WHERE ct.tenant_id = ? AND ct.id = ?
                """, CashTransactionService::map, tenantId, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimento não encontrado."));
    }

    private static TransactionView map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new TransactionView(rs.getObject("id", UUID.class), Direction.valueOf(rs.getString("direction")),
                rs.getString("amount_minor"), rs.getDate("effective_date").toLocalDate(), rs.getString("origin"),
                rs.getString("description"), rs.getObject("category_id", UUID.class), rs.getString("category_name"),
                rs.getObject("financial_account_id", UUID.class), rs.getString("account_name"), rs.getString("counterparty_account_name"));
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
                                  String description, UUID categoryId, String categoryName, UUID accountId, String accountName,
                                  String counterpartyAccountName) {}
    public record TransactionPage(List<TransactionView> items, long total, int page, int size) {}
    public record CreateResult(TransactionView transaction, boolean replay) {}
    public record TransferInput(UUID sourceAccountId, UUID destinationAccountId, long amountMinor,
                                LocalDate effectiveDate, String description) {}
    public record TransferResult(TransactionView outgoingMovement, TransactionView incomingMovement, boolean replay) {}
    private record Existing(UUID id, String hash) {}
    private record AccountState(UUID id, String status) {}
}
