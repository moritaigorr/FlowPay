package com.flowpay.statements.application;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.statements.api.StatementLineReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StatementLineReviewService {
    private static final String OPERATION = "STATEMENT_LINE_REVIEW";
    private final TenantAccess tenants;
    private final StatementLineReader lines;
    private final JdbcTemplate jdbc;

    public StatementLineReviewService(TenantAccess tenants, StatementLineReader lines, JdbcTemplate jdbc) {
        this.tenants = tenants; this.lines = lines; this.jdbc = jdbc;
    }

    @Transactional
    public ReviewResult review(String email, UUID lineId, UUID key, ReviewRequest request) {
        var tenant = tenants.requireSingleTenant(email);
        if (!"OWNER".equals(tenant.role()) && !"MANAGER".equals(tenant.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite revisar extratos.");
        if (key == null || request == null || request.action() == null || request.reason() == null
                || request.reason().isBlank() || request.reason().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a ação e um motivo de até 500 caracteres.");
        var reason = request.reason().trim();
        var hash = hash(lineId + "\u0000" + request.action() + "\u0000" + reason);
        var previous = findEvent(tenant.id(), key);
        if (previous != null) return replay(previous, hash);
        var line = lines.lockLine(tenant.id(), lineId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha do extrato não encontrada."));
        previous = findEvent(tenant.id(), key);
        if (previous != null) return replay(previous, hash);
        String next = switch (request.action()) {
            case MARK_REVIEWED -> "REVIEWED";
            case IGNORE_LINE -> "IGNORED";
            case REOPEN -> "PENDING";
        };
        if (!"ACCEPTED".equals(line.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Somente linhas aceitas podem ser revisadas.");
        var actor = tenants.requireUserId(email);
        int inserted = jdbc.update("""
                INSERT INTO statement_line_review_event
                  (id, tenant_id, financial_account_id, statement_line_id, action, reason, operation, idempotency_key, payload_hash, actor_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, operation, idempotency_key) DO NOTHING
                """, UUID.randomUUID(), tenant.id(), line.accountId(), lineId, request.action().name(), reason,
                OPERATION, key, hash, actor);
        if (inserted == 0) {
            var raced = findEvent(tenant.id(), key);
            if (raced != null) return replay(raced, hash);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A revisão foi concorrida por outra solicitação.");
        }
        if ("PENDING".equals(next)) {
            jdbc.update("UPDATE statement_line SET review_status='PENDING', review_reason=NULL, review_updated_by_user_id=NULL, review_updated_at=NULL WHERE tenant_id=? AND id=?",
                    tenant.id(), lineId);
        } else {
            jdbc.update("UPDATE statement_line SET review_status=?, review_reason=?, review_updated_by_user_id=?, review_updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",
                    next, reason, actor, tenant.id(), lineId);
        }
        return new ReviewResult(lineId, next, false);
    }

    private Event findEvent(UUID tenant, UUID key) {
        return jdbc.query("SELECT statement_line_id, payload_hash, action FROM statement_line_review_event WHERE tenant_id=? AND operation=? AND idempotency_key=?",
                (rs, row) -> new Event(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), tenant, OPERATION, key)
                .stream().findFirst().orElse(null);
    }
    private static ReviewResult replay(Event event, String hash) {
        if (!MessageDigest.isEqual(event.hash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A chave de idempotência já foi usada com outra revisão.");
        return new ReviewResult(event.lineId(), switch (event.action()) { case "MARK_REVIEWED" -> "REVIEWED"; case "IGNORE_LINE" -> "IGNORED"; default -> "PENDING"; }, true);
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record Event(UUID lineId, String hash, String action) {}
    public enum Action { MARK_REVIEWED, IGNORE_LINE, REOPEN }
    public record ReviewRequest(Action action, String reason) {}
    public record ReviewResult(UUID statementLineId, String reviewStatus, boolean replay) {}
}
