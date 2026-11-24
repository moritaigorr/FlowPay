package com.flowpay.reconciliation.application;

import com.flowpay.finance.api.FinanceReconciliationReader;
import com.flowpay.identity.api.TenantAccess;
import com.flowpay.identity.api.TenantRole;
import com.flowpay.statements.api.StatementLineReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReconciliationCandidateIgnoreService {
    private static final String IGNORE = "RECONCILIATION_CANDIDATE_IGNORE";
    private static final String RESTORE = "RECONCILIATION_CANDIDATE_RESTORE";
    private static final int MAX_IGNORED_CANDIDATES_PER_LINE = 100;
    private final TenantAccess tenants;
    private final StatementLineReader lines;
    private final FinanceReconciliationReader finance;
    private final JdbcTemplate jdbc;

    public ReconciliationCandidateIgnoreService(TenantAccess tenants, StatementLineReader lines,
            FinanceReconciliationReader finance, JdbcTemplate jdbc) {
        this.tenants = tenants; this.lines = lines; this.finance = finance; this.jdbc = jdbc;
    }

    @Transactional
    public CandidateResult ignore(String email, UUID lineId, UUID movementId, UUID key, CandidateRequest request) {
        var tenant = tenants.requireSingleTenant(email);
        requireManager(tenant.role());
        String reason = validateReason(key, request);
        var payloadHash = hash(lineId + "\u0000" + movementId + "\u0000" + reason);
        lockKey(tenant.id(), IGNORE, key);
        var replay = findByKey(tenant.id(), IGNORE, key);
        if (replay != null) return candidateReplay(replay, payloadHash);
        var line = lines.lockLine(tenant.id(), lineId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha do extrato não encontrada."));
        if (!"ACCEPTED".equals(line.status()) || "IGNORED".equals(line.reviewStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A linha não está disponível para ignorar candidatos.");
        if (hasActiveMatch(tenant.id(), lineId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Desfaça o vínculo ativo antes de ignorar um candidato.");
        var movement = finance.findLinkableMovement(tenant.id(), line.accountId(), movementId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimento não encontrado nesta conta."));
        if (!"BRL".equals(movement.currency()) || !line.direction().equals(movement.direction())
                || !line.amountMinor().equals(movement.amountMinor()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O movimento não corresponde ao valor, direção e moeda da linha.");
        if (Math.abs(java.time.temporal.ChronoUnit.DAYS.between(line.date(), movement.effectiveDate())) > 3)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O movimento está fora da janela de três dias desta linha.");
        if (hasActiveSuppression(tenant.id(), lineId, movementId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este candidato já foi ignorado para a linha.");
        Long ignoredCount = jdbc.queryForObject("SELECT count(*) FROM reconciliation_candidate_suppression WHERE tenant_id=? AND statement_line_id=? AND restored_at IS NULL",
                Long.class, tenant.id(), lineId);
        if (ignoredCount != null && ignoredCount >= MAX_IGNORED_CANDIDATES_PER_LINE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A linha atingiu o limite de 100 candidatos ignorados. Restaure algum antes de continuar.");
        if (hasActiveMovementMatch(tenant.id(), movementId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O movimento já está conciliado com outra linha.");
        var actor = tenants.requireUserId(email);
        int inserted = jdbc.update("""
                INSERT INTO reconciliation_candidate_suppression
                  (id, tenant_id, financial_account_id, statement_line_id, cash_transaction_id, reason,
                   operation, idempotency_key, payload_hash, created_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, operation, idempotency_key) DO NOTHING
                """, UUID.randomUUID(), tenant.id(), line.accountId(), lineId, movementId, reason,
                IGNORE, key, payloadHash, actor);
        if (inserted == 0) {
            replay = findByKey(tenant.id(), IGNORE, key);
            if (replay != null) return candidateReplay(replay, payloadHash);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A supressão foi concorrida por outra solicitação.");
        }
        var suppression = findByKey(tenant.id(), IGNORE, key);
        return new CandidateResult(suppression.id(), lineId, movementId, reason, false);
    }

    @Transactional
    public CandidateResult restore(String email, UUID lineId, UUID movementId, UUID key, CandidateRequest request) {
        var tenant = tenants.requireSingleTenant(email);
        requireManager(tenant.role());
        String reason = validateReason(key, request);
        var payloadHash = hash(lineId + "\u0000" + movementId + "\u0000" + reason);
        lockKey(tenant.id(), RESTORE, key);
        var replay = findByKey(tenant.id(), RESTORE, key);
        if (replay != null) return candidateReplay(replay, payloadHash);
        lines.lockLine(tenant.id(), lineId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha do extrato não encontrada."));
        var active = jdbc.query("""
                SELECT id, payload_hash FROM reconciliation_candidate_suppression
                WHERE tenant_id=? AND statement_line_id=? AND cash_transaction_id=? AND restored_at IS NULL
                FOR UPDATE
                """, (rs, row) -> new KeyRecord(rs.getObject(1, UUID.class), rs.getString(2), lineId, movementId, reason),
                tenant.id(), lineId, movementId).stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Este candidato não está ignorado."));
        int updated = jdbc.update("""
                UPDATE reconciliation_candidate_suppression
                SET restored_by_user_id=?, restored_at=CURRENT_TIMESTAMP, restore_reason=?, restore_operation=?,
                    restore_idempotency_key=?, restore_payload_hash=?
                WHERE tenant_id=? AND id=? AND restored_at IS NULL
                """, tenants.requireUserId(email), reason, RESTORE, key, payloadHash, tenant.id(), active.id());
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "O candidato mudou durante a restauração.");
        return new CandidateResult(active.id(), lineId, movementId, reason, false);
    }

    @Transactional(readOnly = true)
    public List<SuppressedCandidate> active(UUID tenantId, UUID lineId) {
        return jdbc.query("""
                SELECT s.id, s.cash_transaction_id, s.reason, s.created_at, ct.financial_account_id, ct.direction, ct.amount_minor::text AS amount_minor,
                       ct.currency, ct.effective_date, ct.origin, ct.description, a.name AS account_name
                FROM reconciliation_candidate_suppression s
                JOIN cash_transaction ct ON ct.tenant_id=s.tenant_id AND ct.financial_account_id=s.financial_account_id AND ct.id=s.cash_transaction_id
                JOIN financial_account a ON a.tenant_id=ct.tenant_id AND a.id=ct.financial_account_id
                WHERE s.tenant_id=? AND s.statement_line_id=? AND s.restored_at IS NULL
                ORDER BY s.created_at DESC, s.id
                """, (rs, row) -> new SuppressedCandidate(rs.getObject("id", UUID.class),
                new FinanceReconciliationReader.MovementCandidate(rs.getObject("cash_transaction_id", UUID.class),
                        rs.getObject("financial_account_id", UUID.class), rs.getString("account_name"), rs.getString("direction"), rs.getString("amount_minor"),
                        rs.getString("currency"), rs.getDate("effective_date").toLocalDate(), rs.getString("origin"), rs.getString("description")),
                rs.getString("reason"), rs.getTimestamp("created_at").toInstant()), tenantId, lineId);
    }

    private boolean hasActiveMatch(UUID tenantId, UUID lineId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM reconciliation_match WHERE tenant_id=? AND statement_line_id=? AND reversed_at IS NULL)",
                Boolean.class, tenantId, lineId));
    }
    private boolean hasActiveMovementMatch(UUID tenantId, UUID movementId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM reconciliation_match WHERE tenant_id=? AND cash_transaction_id=? AND reversed_at IS NULL)",
                Boolean.class, tenantId, movementId));
    }
    private boolean hasActiveSuppression(UUID tenantId, UUID lineId, UUID movementId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM reconciliation_candidate_suppression WHERE tenant_id=? AND statement_line_id=? AND cash_transaction_id=? AND restored_at IS NULL)",
                Boolean.class, tenantId, lineId, movementId));
    }
    private KeyRecord findByKey(UUID tenant, String operation, UUID key) {
        String operationColumn = IGNORE.equals(operation) ? "operation" : "restore_operation";
        String keyColumn = IGNORE.equals(operation) ? "idempotency_key" : "restore_idempotency_key";
        return jdbc.query("SELECT id, " + (IGNORE.equals(operation) ? "payload_hash" : "restore_payload_hash") +
                ", statement_line_id, cash_transaction_id, " + (IGNORE.equals(operation) ? "reason" : "restore_reason") +
                " FROM reconciliation_candidate_suppression WHERE tenant_id=? AND " + operationColumn + "=? AND " + keyColumn + "=?",
                (rs, row) -> new KeyRecord(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getString(5)), tenant, operation, key).stream().findFirst().orElse(null);
    }
    private static CandidateResult candidateReplay(KeyRecord record, String hash) {
        if (!MessageDigest.isEqual(record.payloadHash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A chave de idempotência já foi usada com outra supressão.");
        return new CandidateResult(record.id(), record.lineId(), record.movementId(), record.reason(), true);
    }
    private void lockKey(UUID tenant, String operation, UUID key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { }, tenant + ":" + operation + ":" + key);
    }
    private static String validateReason(UUID key, CandidateRequest request) {
        if (key == null || request == null || request.reason() == null || request.reason().isBlank() || request.reason().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um motivo de até 500 caracteres.");
        return request.reason().trim();
    }
    private static void requireManager(String role) {
        if (!TenantRole.canManageBusinessData(role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite ignorar candidatos.");
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private record KeyRecord(UUID id, String payloadHash, UUID lineId, UUID movementId, String reason) {}
    public record CandidateRequest(String reason) {}
    public record CandidateResult(UUID suppressionId, UUID statementLineId, UUID cashTransactionId, String reason, boolean replay) {}
    public record SuppressedCandidate(UUID suppressionId, FinanceReconciliationReader.MovementCandidate movement,
                                      String reason, Instant ignoredAt) {}
}
