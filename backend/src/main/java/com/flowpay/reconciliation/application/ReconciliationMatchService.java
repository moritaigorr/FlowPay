package com.flowpay.reconciliation.application;

import com.flowpay.finance.api.FinanceReconciliationReader;
import com.flowpay.finance.api.FinanceReconciliationWriter;
import com.flowpay.identity.api.TenantAccess;
import com.flowpay.statements.api.StatementLineReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReconciliationMatchService {
    private static final String OPERATION = "STATEMENT_RECONCILIATION";
    private final TenantAccess tenantAccess;
    private final FinanceReconciliationReader finance;
    private final FinanceReconciliationWriter financeWriter;
    private final StatementLineReader statementLines;
    private final JdbcTemplate jdbc;

    public ReconciliationMatchService(TenantAccess tenantAccess, FinanceReconciliationReader finance,
                                     FinanceReconciliationWriter financeWriter, StatementLineReader statementLines,
                                     JdbcTemplate jdbc) {
        this.tenantAccess = tenantAccess;
        this.finance = finance;
        this.financeWriter = financeWriter;
        this.statementLines = statementLines;
        this.jdbc = jdbc;
    }

    @Transactional
    public MatchResult match(String email, UUID statementLineId, UUID idempotencyKey, MatchRequest request) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireManager(tenant.role());
        validateRequest(request);
        var payloadHash = payloadHash(statementLineId, request);
        var replay = findByKey(tenant.id(), idempotencyKey);
        if (replay != null) return replay(replay, payloadHash);

        var line = statementLines.lockLine(tenant.id(), statementLineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha do extrato não encontrada."));
        if (!"ACCEPTED".equals(line.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Apenas linhas válidas e não repetidas podem ser conciliadas.");
        if ("IGNORED".equals(line.reviewStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta linha foi ignorada durante a revisão e não pode ser conciliada.");

        // A prior request with the same key may have committed while this request waited for the line lock.
        replay = findByKey(tenant.id(), idempotencyKey);
        if (replay != null) return replay(replay, payloadHash);
        if (hasActiveLineMatch(tenant.id(), statementLineId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta linha já possui uma conciliação ativa.");

        FinanceReconciliationReader.MovementCandidate movement;
        if (request.decision() == Decision.LINK_EXISTING) {
            var candidate = finance.findLinkableMovement(tenant.id(), line.accountId(), request.cashTransactionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimento elegível não encontrado para esta conta."));
            if (!"BRL".equals(candidate.currency()) || !line.direction().equals(candidate.direction())
                    || !line.amountMinor().equals(candidate.amountMinor()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "O movimento mudou ou não corresponde ao valor, direção e moeda da linha.");
            if (hasActiveMovementMatch(tenant.id(), candidate.id()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Este movimento já está conciliado com outra linha.");
            movement = candidate;
        } else {
            movement = financeWriter.createFromStatement(tenant.id(), line.accountId(), line.direction(),
                    Long.parseLong(line.amountMinor()), line.date(), line.description(), request.categoryId());
        }

        var matchId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO reconciliation_match
                      (id, tenant_id, financial_account_id, statement_line_id, cash_transaction_id, decision,
                       operation, idempotency_key, payload_hash, created_by_user_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, matchId, tenant.id(), line.accountId(), statementLineId, movement.id(), request.decision().name(),
                    OPERATION, idempotencyKey, payloadHash, tenantAccess.requireUserId(email));
        } catch (DuplicateKeyException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Outra solicitação já conciliou esta linha ou este movimento.", conflict);
        }
        return new MatchResult(matchId, statementLineId, movement.id(), request.decision().name(), false, true);
    }

    @Transactional
    public UnlinkResult unlink(String email, UUID statementLineId, UUID idempotencyKey, UnlinkRequest request) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireManager(tenant.role());
        if (idempotencyKey == null || request == null || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o motivo do desfazer vínculo (até 500 caracteres).");
        var reason = request.reason().trim();
        var hash = payloadHash(statementLineId + "\u0000" + reason);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { }, tenant.id() + ":RECONCILIATION_UNLINK:" + idempotencyKey);
        var existing = jdbc.query("SELECT id, reversal_payload_hash, decision, statement_line_id FROM reconciliation_match WHERE tenant_id=? AND reversal_operation='RECONCILIATION_UNLINK' AND reversal_idempotency_key=?",
                (rs, row) -> new UnlinkRecord(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class)), tenant.id(), idempotencyKey)
                .stream().findFirst().orElse(null);
        if (existing != null) return unlinkReplay(existing, hash);
        var line = statementLines.lockLine(tenant.id(), statementLineId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha do extrato não encontrada."));
        existing = jdbc.query("SELECT id, reversal_payload_hash, decision, statement_line_id FROM reconciliation_match WHERE tenant_id=? AND reversal_operation='RECONCILIATION_UNLINK' AND reversal_idempotency_key=?",
                (rs, row) -> new UnlinkRecord(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class)), tenant.id(), idempotencyKey)
                .stream().findFirst().orElse(null);
        if (existing != null) return unlinkReplay(existing, hash);
        var active = jdbc.query("SELECT id, decision, statement_line_id FROM reconciliation_match WHERE tenant_id=? AND statement_line_id=? AND reversed_at IS NULL FOR UPDATE",
                (rs, row) -> new UnlinkRecord(rs.getObject(1, UUID.class), null, rs.getString(2), rs.getObject(3, UUID.class)), tenant.id(), statementLineId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Esta linha não possui vínculo ativo."));
        jdbc.update("UPDATE reconciliation_match SET reversed_at=CURRENT_TIMESTAMP, reversed_by_user_id=?, reversal_reason=?, reversal_operation='RECONCILIATION_UNLINK', reversal_idempotency_key=?, reversal_payload_hash=? WHERE tenant_id=? AND id=? AND reversed_at IS NULL",
                tenantAccess.requireUserId(email), reason, idempotencyKey, hash, tenant.id(), active.id());
        // Unlinking only removes the reconciliation relation. A movement created during matching remains in cash.
        return new UnlinkResult(active.id(), statementLineId, active.decision(), false, "CREATE_MOVEMENT".equals(active.decision()));
    }

    private static UnlinkResult unlinkReplay(UnlinkRecord record, String hash) {
        if (!MessageDigest.isEqual(record.payloadHash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A chave de idempotência já foi usada com outro motivo.");
        return new UnlinkResult(record.id(), record.lineId(), record.decision(), true, "CREATE_MOVEMENT".equals(record.decision()));
    }

    private static String payloadHash(String canonical) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 indisponível", impossible); }
    }

    private MatchRecord findByKey(UUID tenantId, UUID idempotencyKey) {
        return jdbc.query("""
                SELECT id, statement_line_id, cash_transaction_id, decision, payload_hash, reversed_at
                FROM reconciliation_match WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?
                """, (rs, row) -> new MatchRecord(rs.getObject("id", UUID.class),
                rs.getObject("statement_line_id", UUID.class), rs.getObject("cash_transaction_id", UUID.class),
                rs.getString("decision"), rs.getString("payload_hash"), rs.getTimestamp("reversed_at") != null),
                tenantId, OPERATION, idempotencyKey).stream().findFirst().orElse(null);
    }

    private static MatchResult replay(MatchRecord match, String payloadHash) {
        if (!MessageDigest.isEqual(match.payloadHash().getBytes(StandardCharsets.US_ASCII), payloadHash.getBytes(StandardCharsets.US_ASCII)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A chave de idempotência já foi usada com outra decisão.");
        return new MatchResult(match.id(), match.statementLineId(), match.cashTransactionId(), match.decision(), true, !match.reversed());
    }

    private boolean hasActiveLineMatch(UUID tenantId, UUID lineId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM reconciliation_match WHERE tenant_id = ? AND statement_line_id = ? AND reversed_at IS NULL)
                """, Boolean.class, tenantId, lineId));
    }

    private boolean hasActiveMovementMatch(UUID tenantId, UUID movementId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM reconciliation_match WHERE tenant_id = ? AND cash_transaction_id = ? AND reversed_at IS NULL)
                """, Boolean.class, tenantId, movementId));
    }

    private static void validateRequest(MatchRequest request) {
        if (request == null || request.decision() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha como deseja conciliar a linha.");
        if (request.decision() == Decision.LINK_EXISTING && (request.cashTransactionId() == null || request.categoryId() != null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vincular exige um movimento existente e não aceita categoria nova.");
        if (request.decision() == Decision.CREATE_MOVEMENT && (request.cashTransactionId() != null || request.categoryId() == null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Criar movimento exige uma categoria e não aceita um movimento existente.");
    }

    private static String payloadHash(UUID statementLineId, MatchRequest request) {
        var canonical = statementLineId + "\u0000" + request.decision() + "\u0000" + request.cashTransactionId() + "\u0000" + request.categoryId();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 indisponível", impossible); }
    }

    private static void requireManager(String role) {
        if (!"OWNER".equals(role) && !"MANAGER".equals(role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite confirmar conciliações.");
    }

    private record MatchRecord(UUID id, UUID statementLineId, UUID cashTransactionId, String decision,
                               String payloadHash, boolean reversed) {}
    private record UnlinkRecord(UUID id, String payloadHash, String decision, UUID lineId) {}

    public enum Decision { LINK_EXISTING, CREATE_MOVEMENT }
    public record MatchRequest(Decision decision, UUID cashTransactionId, UUID categoryId) {}
    public record MatchResult(UUID matchId, UUID statementLineId, UUID cashTransactionId,
                              String decision, boolean replay, boolean active) {}
    public record UnlinkRequest(String reason) {}
    public record UnlinkResult(UUID matchId, UUID statementLineId, String decision, boolean replay,
                               boolean cashMovementPreserved) {}
}
