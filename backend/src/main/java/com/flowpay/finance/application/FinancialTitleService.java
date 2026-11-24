package com.flowpay.finance.application;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.identity.api.TenantRole;
import com.flowpay.finance.domain.FinancialAmountRules;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialTitleService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private final JdbcTemplate jdbc;
    private final TenantAccess tenantAccess;

    public FinancialTitleService(JdbcTemplate jdbc, TenantAccess tenantAccess) {
        this.jdbc = jdbc;
        this.tenantAccess = tenantAccess;
    }

    @Transactional(readOnly = true)
    public TitlePage list(String email, TitleKind kind, String query, int page, int size) {
        return list(email, kind, query, null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public TitlePage list(String email, TitleKind kind, String query, TitleStatus status,
                          UUID contactId, LocalDate dueFrom, LocalDate dueTo, int page, int size) {
        validatePage(page, size);
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O início do período deve ser anterior ao fim.");
        }
        var tenant = tenantAccess.requireSingleTenant(email);
        var normalizedQuery = normalize(query);
        var today = LocalDate.now(BUSINESS_ZONE);
        var where = new StringBuilder(" WHERE t.tenant_id = ? AND t.kind = ?");
        var filterArgs = new ArrayList<Object>(List.of(tenant.id(), kind.name()));
        if (normalizedQuery != null) {
            where.append(" AND (t.description ILIKE ? OR c.name ILIKE ?)");
            filterArgs.add("%" + normalizedQuery + "%");
            filterArgs.add("%" + normalizedQuery + "%");
        }
        if (contactId != null) {
            where.append(" AND t.contact_id = ?");
            filterArgs.add(contactId);
        }
        if (dueFrom != null) {
            where.append(" AND t.due_date >= ?");
            filterArgs.add(dueFrom);
        }
        if (dueTo != null) {
            where.append(" AND t.due_date <= ?");
            filterArgs.add(dueTo);
        }
        if (status != null) {
            where.append(switch (status) {
                case OPEN -> " AND t.status = 'OPEN' AND COALESCE(s.settled_minor, 0) = 0 AND t.due_date >= ?";
                case OVERDUE -> " AND t.status = 'OPEN' AND COALESCE(s.settled_minor, 0) = 0 AND t.due_date < ?";
                case PARTIALLY_SETTLED -> " AND t.status = 'OPEN' AND COALESCE(s.settled_minor, 0) > 0 AND t.original_amount_minor > COALESCE(s.settled_minor, 0)";
                case SETTLED -> " AND t.status = 'OPEN' AND t.original_amount_minor = COALESCE(s.settled_minor, 0)";
                case CANCELLED -> " AND t.status = 'CANCELLED'";
            });
            if (status == TitleStatus.OPEN || status == TitleStatus.OVERDUE) filterArgs.add(today);
        }
        var select = titleSelect() + where;
        var total = jdbc.queryForObject("SELECT count(*) FROM (" + select + ") filtered_titles", Long.class, filterArgs.toArray());
        var pageArgs = new ArrayList<>(filterArgs);
        pageArgs.add(size);
        pageArgs.add(page * size);
        var items = jdbc.query(select + " ORDER BY t.due_date, lower(t.description), t.id LIMIT ? OFFSET ?",
                (rs, row) -> mapTitle(rs, today), pageArgs.toArray());
        return new TitlePage(items, total, page, size, today);
    }

    @Transactional(readOnly = true)
    public TitleView get(String email, UUID id) {
        var tenant = tenantAccess.requireSingleTenant(email);
        var today = LocalDate.now(BUSINESS_ZONE);
        return jdbc.query(titleSelect() + " WHERE t.tenant_id = ? AND t.id = ?",
                (rs, row) -> mapTitle(rs, today), tenant.id(), id).stream()
                .findFirst().orElseThrow(FinancialTitleService::notFound);
    }

    @Transactional
    public CreateResult create(String email, TitleKind kind, UUID key, TitleInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var normalized = normalizeInput(input);
        var hash = payloadHash(kind, normalized);
        var prior = findCreation(tenant.id(), kind, key);
        if (prior != null) {
            if (!sameHash(prior.hash(), hash)) throw idempotencyConflict();
            return new CreateResult(get(tenant.id(), prior.id()), true);
        }
        validateReferences(tenant.id(), kind, normalized);
        var id = UUID.randomUUID();
        var inserted = jdbc.update("""
                INSERT INTO financial_title
                  (id, tenant_id, kind, description, contact_id, category_id, category_direction, planned_account_id,
                   original_amount_minor, due_date, notes, creation_key, creation_payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, kind, creation_key) DO NOTHING
                """, id, tenant.id(), kind.name(), normalized.description(), normalized.contactId(),
                normalized.categoryId(), expectedDirection(kind), normalized.plannedAccountId(), normalized.originalAmountMinor(),
                normalized.dueDate(), normalized.notes(), key, hash);
        if (inserted == 0) {
            var existing = findCreation(tenant.id(), kind, key);
            if (existing == null || !sameHash(existing.hash(), hash)) throw idempotencyConflict();
            return new CreateResult(get(tenant.id(), existing.id()), true);
        }
        return new CreateResult(get(tenant.id(), id), false);
    }

    @Transactional
    public TitleView update(String email, UUID id, TitleInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var current = jdbc.query("SELECT kind, status FROM financial_title WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> new TitleState(TitleKind.valueOf(rs.getString("kind")), rs.getString("status")), tenant.id(), id)
                .stream().findFirst().orElseThrow(FinancialTitleService::notFound);
        if (!"OPEN".equals(current.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Títulos cancelados não podem ser alterados.");
        if (hasSettlements(tenant.id(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Um título com baixas registradas não pode ser alterado.");
        var kind = current.kind();
        var normalized = normalizeInput(input);
        validateReferences(tenant.id(), kind, normalized);
        jdbc.update("""
                UPDATE financial_title
                SET description = ?, contact_id = ?, category_id = ?, category_direction = ?, planned_account_id = ?,
                    original_amount_minor = ?, due_date = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ? AND status = 'OPEN'
                """, normalized.description(), normalized.contactId(), normalized.categoryId(), expectedDirection(kind),
                normalized.plannedAccountId(), normalized.originalAmountMinor(), normalized.dueDate(), normalized.notes(), tenant.id(), id);
        return get(tenant.id(), id);
    }

    @Transactional
    public TitleView cancel(String email, UUID id, String reason) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var status = jdbc.query("SELECT status FROM financial_title WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> rs.getString("status"), tenant.id(), id).stream().findFirst().orElseThrow(FinancialTitleService::notFound);
        if (!"OPEN".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "O título já foi cancelado.");
        if (hasSettlements(tenant.id(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Um título com baixas registradas não pode ser cancelado.");
        jdbc.update("""
                UPDATE financial_title
                SET status = 'CANCELLED', cancellation_reason = ?, cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ?
                """, reason.trim(), tenant.id(), id);
        return get(tenant.id(), id);
    }

    @Transactional
    public SettlementResult settle(String email, UUID titleId, UUID key, SettlementInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var hash = settlementHash(titleId, input);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { rs.next(); return null; },
                tenant.id() + ":TITLE_SETTLEMENT:" + key);
        var prior = findSettlement(tenant.id(), key);
        if (prior != null) {
            if (!sameHash(prior.hash(), hash)) throw idempotencyConflict();
            return new SettlementResult(get(tenant.id(), prior.titleId()), true);
        }
        var state = jdbc.query("SELECT kind, status, original_amount_minor, category_id, category_direction FROM financial_title WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> new LockedTitle(TitleKind.valueOf(rs.getString("kind")), rs.getString("status"), rs.getLong("original_amount_minor"),
                        rs.getObject("category_id", UUID.class), rs.getString("category_direction")), tenant.id(), titleId)
                .stream().findFirst().orElseThrow(FinancialTitleService::notFound);
        if (!"OPEN".equals(state.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este título não está disponível para baixa.");
        var account = jdbc.query("SELECT status FROM financial_account WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> rs.getString("status"), tenant.id(), input.accountId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma conta desta empresa."));
        if (!"ACTIVE".equals(account)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A conta selecionada está arquivada.");
        var settled = jdbc.queryForObject("SELECT COALESCE(sum(amount_minor), 0) FROM financial_settlement WHERE tenant_id = ? AND financial_title_id = ?", Long.class, tenant.id(), titleId);
        var remaining = FinancialAmountRules.remaining(state.originalAmountMinor(), settled);
        try {
            FinancialAmountRules.requireAvailable(input.amountMinor(), remaining);
        } catch (IllegalArgumentException invalid) {
            if (input.amountMinor() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor da baixa precisa ser positivo.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O valor da baixa excede o saldo em aberto do título.");
        }
        var movementId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description, category_id, category_direction)
                VALUES (?, ?, ?, ?, ?, ?, 'TITLE_SETTLEMENT', ?, ?, ?)
                """, movementId, tenant.id(), input.accountId(), expectedDirection(state.kind()), input.amountMinor(), input.effectiveDate(),
                truncate((state.kind() == TitleKind.RECEIVABLE ? "Recebimento: " : "Pagamento: ") + get(tenant.id(), titleId).description(), 160),
                state.categoryId(), state.categoryDirection());
        jdbc.update("""
                INSERT INTO financial_settlement (id, tenant_id, financial_title_id, cash_transaction_id, amount_minor, effective_date, idempotency_key, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenant.id(), titleId, movementId, input.amountMinor(), input.effectiveDate(), key, hash);
        return new SettlementResult(get(tenant.id(), titleId), false);
    }

    @Transactional(readOnly = true)
    public List<SettlementView> listSettlements(String email, UUID titleId) {
        var tenant = tenantAccess.requireSingleTenant(email);
        if (!titleExists(tenant.id(), titleId)) throw notFound();
        return jdbc.query("""
                SELECT s.id, s.amount_minor::text AS amount_minor, s.effective_date,
                       COALESCE(r.reversed_minor, 0)::text AS reversed_minor,
                       (s.amount_minor - COALESCE(r.reversed_minor, 0))::text AS remaining_minor
                FROM financial_settlement s
                LEFT JOIN (SELECT tenant_id, financial_settlement_id, sum(amount_minor) AS reversed_minor
                           FROM financial_settlement_reversal GROUP BY tenant_id, financial_settlement_id) r
                  ON r.tenant_id = s.tenant_id AND r.financial_settlement_id = s.id
                WHERE s.tenant_id = ? AND s.financial_title_id = ?
                ORDER BY s.created_at, s.id
                """, (rs, row) -> new SettlementView(rs.getObject("id", UUID.class), rs.getString("amount_minor"),
                rs.getString("reversed_minor"), rs.getString("remaining_minor"), rs.getDate("effective_date").toLocalDate()), tenant.id(), titleId);
    }

    @Transactional
    public ReversalResult reverse(String email, UUID titleId, UUID settlementId, UUID key, ReversalInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var normalizedReason = input.reason().trim();
        var normalized = new ReversalInput(input.amountMinor(), input.effectiveDate(), normalizedReason);
        var hash = reversalHash(titleId, settlementId, normalized);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> { rs.next(); return null; },
                tenant.id() + ":TITLE_SETTLEMENT_REVERSAL:" + key);
        var prior = findReversal(tenant.id(), key);
        if (prior != null) {
            if (!sameHash(prior.hash(), hash)) throw idempotencyConflict();
            return new ReversalResult(get(tenant.id(), prior.titleId()), true);
        }
        var titleStatus = jdbc.query("SELECT status FROM financial_title WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> rs.getString("status"), tenant.id(), titleId).stream().findFirst().orElseThrow(FinancialTitleService::notFound);
        if (!"OPEN".equals(titleStatus)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este título não está disponível para reversão.");
        var source = jdbc.query("""
                SELECT s.amount_minor, ct.financial_account_id, ct.direction, ct.description, ct.category_id, ct.category_direction
                FROM financial_settlement s
                JOIN cash_transaction ct ON ct.tenant_id = s.tenant_id AND ct.id = s.cash_transaction_id
                WHERE s.tenant_id = ? AND s.financial_title_id = ? AND s.id = ?
                FOR UPDATE OF s
                """, (rs, row) -> new ReversibleSettlement(rs.getLong("amount_minor"), rs.getObject("financial_account_id", UUID.class),
                rs.getString("direction"), rs.getString("description"), rs.getObject("category_id", UUID.class), rs.getString("category_direction")), tenant.id(), titleId, settlementId)
                .stream().findFirst().orElseThrow(FinancialTitleService::notFound);
        var alreadyReversed = jdbc.queryForObject("SELECT COALESCE(sum(amount_minor), 0) FROM financial_settlement_reversal WHERE tenant_id = ? AND financial_settlement_id = ?", Long.class, tenant.id(), settlementId);
        var reversible = FinancialAmountRules.remaining(source.amountMinor(), alreadyReversed);
        try {
            FinancialAmountRules.requireAvailable(input.amountMinor(), reversible);
        } catch (IllegalArgumentException invalid) {
            if (input.amountMinor() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor da reversão precisa ser positivo.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O valor excede o saldo ainda não revertido desta baixa.");
        }
        jdbc.query("SELECT id FROM financial_account WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), tenant.id(), source.accountId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "A conta do movimento original não está disponível."));
        var movementId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_transaction
                  (id, tenant_id, financial_account_id, direction, amount_minor, effective_date, origin, description, category_id, category_direction)
                VALUES (?, ?, ?, ?, ?, ?, 'SETTLEMENT_REVERSAL', ?, ?, ?)
                """, movementId, tenant.id(), source.accountId(), "INFLOW".equals(source.direction()) ? "OUTFLOW" : "INFLOW",
                input.amountMinor(), input.effectiveDate(), truncate("Estorno: " + source.description(), 160), source.categoryId(), source.categoryDirection());
        jdbc.update("""
                INSERT INTO financial_settlement_reversal
                  (id, tenant_id, financial_title_id, financial_settlement_id, cash_transaction_id,
                   amount_minor, effective_date, reason, idempotency_key, payload_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenant.id(), titleId, settlementId, movementId, input.amountMinor(),
                input.effectiveDate(), normalizedReason, key, hash);
        return new ReversalResult(get(tenant.id(), titleId), false);
    }

    private boolean titleExists(UUID tenantId, UUID titleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM financial_title WHERE tenant_id = ? AND id = ?)", Boolean.class, tenantId, titleId));
    }

    private ExistingReversal findReversal(UUID tenantId, UUID key) {
        return jdbc.query("SELECT financial_title_id, payload_hash FROM financial_settlement_reversal WHERE tenant_id = ? AND operation = 'TITLE_SETTLEMENT_REVERSAL' AND idempotency_key = ?",
                (rs, row) -> new ExistingReversal(rs.getObject("financial_title_id", UUID.class), rs.getString("payload_hash")), tenantId, key)
                .stream().findFirst().orElse(null);
    }

    private ExistingSettlement findSettlement(UUID tenantId, UUID key) {
        return jdbc.query("SELECT financial_title_id, payload_hash FROM financial_settlement WHERE tenant_id = ? AND operation = 'TITLE_SETTLEMENT' AND idempotency_key = ?",
                (rs, row) -> new ExistingSettlement(rs.getObject("financial_title_id", UUID.class), rs.getString("payload_hash")), tenantId, key)
                .stream().findFirst().orElse(null);
    }

    private boolean hasSettlements(UUID tenantId, UUID titleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM financial_settlement WHERE tenant_id = ? AND financial_title_id = ?)", Boolean.class, tenantId, titleId));
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private void validateReferences(UUID tenantId, TitleKind kind, TitleInput input) {
        var contact = jdbc.query("""
                SELECT is_customer, is_supplier FROM contact WHERE tenant_id = ? AND id = ?
                """, (rs, row) -> new ContactRoles(rs.getBoolean("is_customer"), rs.getBoolean("is_supplier")), tenantId, input.contactId())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um contato válido desta empresa."));
        if (kind == TitleKind.RECEIVABLE && !contact.customer() || kind == TitleKind.PAYABLE && !contact.supplier()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    kind == TitleKind.RECEIVABLE ? "O contato precisa estar marcado como cliente." : "O contato precisa estar marcado como fornecedor.");
        }
        var expectedDirection = kind == TitleKind.RECEIVABLE ? "INFLOW" : "OUTFLOW";
        var categoryExists = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM financial_category WHERE tenant_id = ? AND id = ? AND direction = ? AND status = 'ACTIVE')
                """, Boolean.class, tenantId, input.categoryId(), expectedDirection));
        if (!categoryExists) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma categoria ativa desta empresa e compatível com o título.");
        if (input.plannedAccountId() != null) {
            var accountExists = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM financial_account WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE')
                    """, Boolean.class, tenantId, input.plannedAccountId()));
            if (!accountExists) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma conta ativa desta empresa.");
        }
    }

    private ExistingCreation findCreation(UUID tenantId, TitleKind kind, UUID key) {
        return jdbc.query("""
                SELECT id, creation_payload_hash FROM financial_title
                WHERE tenant_id = ? AND kind = ? AND creation_key = ?
                """, (rs, row) -> new ExistingCreation(rs.getObject("id", UUID.class), rs.getString("creation_payload_hash")),
                tenantId, kind.name(), key).stream().findFirst().orElse(null);
    }

    private static boolean sameHash(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private static String expectedDirection(TitleKind kind) {
        return kind == TitleKind.RECEIVABLE ? "INFLOW" : "OUTFLOW";
    }

    private TitleView get(UUID tenantId, UUID id) {
        var today = LocalDate.now(BUSINESS_ZONE);
        return jdbc.query(titleSelect() + " WHERE t.tenant_id = ? AND t.id = ?",
                (rs, row) -> mapTitle(rs, today), tenantId, id).stream().findFirst().orElseThrow(FinancialTitleService::notFound);
    }

    private static String titleSelect() {
        return """
                SELECT t.id, t.kind, t.description, t.contact_id, c.name AS contact_name,
                       t.category_id, cat.name AS category_name, t.planned_account_id, a.name AS account_name,
                       t.original_amount_minor::text AS original_amount_minor, t.currency, t.due_date,
                       COALESCE(s.settled_minor, 0)::text AS settled_minor,
                       (t.original_amount_minor - COALESCE(s.settled_minor, 0))::text AS remaining_minor,
                       t.notes, t.status, t.cancellation_reason, t.created_at, t.updated_at
                FROM financial_title t
                JOIN contact c ON c.tenant_id = t.tenant_id AND c.id = t.contact_id
                JOIN financial_category cat ON cat.tenant_id = t.tenant_id AND cat.id = t.category_id
                LEFT JOIN financial_account a ON a.tenant_id = t.tenant_id AND a.id = t.planned_account_id
                LEFT JOIN (SELECT s.tenant_id, s.financial_title_id,
                                  sum(s.amount_minor - COALESCE(r.reversed_minor, 0)) AS settled_minor
                           FROM financial_settlement s
                           LEFT JOIN (SELECT tenant_id, financial_settlement_id, sum(amount_minor) AS reversed_minor
                                      FROM financial_settlement_reversal GROUP BY tenant_id, financial_settlement_id) r
                             ON r.tenant_id = s.tenant_id AND r.financial_settlement_id = s.id
                           GROUP BY s.tenant_id, s.financial_title_id) s
                  ON s.tenant_id = t.tenant_id AND s.financial_title_id = t.id
                """;
    }

    private static TitleView mapTitle(java.sql.ResultSet rs, LocalDate asOf) throws java.sql.SQLException {
        var dueDate = rs.getDate("due_date").toLocalDate();
        var status = rs.getString("status");
        var settledMinor = rs.getString("settled_minor");
        var remainingMinor = rs.getString("remaining_minor");
        var derivedStatus = "CANCELLED".equals(status) ? TitleStatus.CANCELLED
                : "0".equals(remainingMinor) ? TitleStatus.SETTLED
                : Long.parseLong(settledMinor) > 0 ? TitleStatus.PARTIALLY_SETTLED
                : dueDate.isBefore(asOf) ? TitleStatus.OVERDUE : TitleStatus.OPEN;
        return new TitleView(rs.getObject("id", UUID.class), TitleKind.valueOf(rs.getString("kind")),
                rs.getString("description"), rs.getObject("contact_id", UUID.class), rs.getString("contact_name"),
                rs.getObject("category_id", UUID.class), rs.getString("category_name"),
                rs.getObject("planned_account_id", UUID.class), rs.getString("account_name"),
                rs.getString("original_amount_minor"), settledMinor, remainingMinor, rs.getString("currency"), dueDate,
                rs.getString("notes"), derivedStatus, rs.getString("cancellation_reason"), asOf,
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static TitleInput normalizeInput(TitleInput input) {
        return new TitleInput(input.description().trim(), input.contactId(), input.categoryId(), input.plannedAccountId(),
                input.originalAmountMinor(), input.dueDate(), normalize(input.notes()));
    }

    private static String payloadHash(TitleKind kind, TitleInput input) {
        var canonical = String.join("\u0000", kind.name(), input.description(), input.contactId().toString(), input.categoryId().toString(),
                input.plannedAccountId() == null ? "" : input.plannedAccountId().toString(),
                Long.toString(input.originalAmountMinor()), input.dueDate().toString(), input.notes() == null ? "" : input.notes());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String settlementHash(UUID titleId, SettlementInput input) {
        var canonical = String.join("\u0000", titleId.toString(), input.accountId().toString(), Long.toString(input.amountMinor()), input.effectiveDate().toString());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private static String reversalHash(UUID titleId, UUID settlementId, ReversalInput input) {
        var canonical = String.join("\u0000", titleId.toString(), settlementId.toString(), Long.toString(input.amountMinor()),
                input.effectiveDate().toString(), input.reason());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho fora dos limites permitidos.");
        }
    }

    private static void requireBusinessWriter(String role) {
        if (!TenantRole.canManageBusinessData(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite alterar títulos.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Título financeiro não encontrado.");
    }

    private static ResponseStatusException idempotencyConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A chave de idempotência já foi usada com outros dados.");
    }

    public enum TitleKind { RECEIVABLE, PAYABLE }
    public enum TitleStatus { OPEN, OVERDUE, PARTIALLY_SETTLED, SETTLED, CANCELLED }
    public record TitleInput(String description, UUID contactId, UUID categoryId, UUID plannedAccountId,
                             long originalAmountMinor, LocalDate dueDate, String notes) {}
    public record TitleView(UUID id, TitleKind kind, String description, UUID contactId, String contactName,
                            UUID categoryId, String categoryName, UUID plannedAccountId, String plannedAccountName,
                            String originalAmountMinor, String settledAmountMinor, String remainingAmountMinor, String currency, LocalDate dueDate, String notes,
                            TitleStatus status, String cancellationReason, LocalDate asOf,
                            Instant createdAt, Instant updatedAt) {
        @Override public String toString() { return "TitleView[REDACTED]"; }
    }
    public record TitlePage(List<TitleView> items, long total, int page, int size, LocalDate asOf) {}
    public record CreateResult(TitleView title, boolean replay) {}
    public record SettlementInput(UUID accountId, long amountMinor, LocalDate effectiveDate) {}
    public record SettlementResult(TitleView title, boolean replay) {}
    public record SettlementView(UUID id, String amountMinor, String reversedAmountMinor, String remainingAmountMinor, LocalDate effectiveDate) {}
    public record ReversalInput(long amountMinor, LocalDate effectiveDate, String reason) {}
    public record ReversalResult(TitleView title, boolean replay) {}
    private record ExistingCreation(UUID id, String hash) {}
    private record TitleState(TitleKind kind, String status) {}
    private record LockedTitle(TitleKind kind, String status, long originalAmountMinor, UUID categoryId, String categoryDirection) {}
    private record ExistingSettlement(UUID titleId, String hash) {}
    private record ExistingReversal(UUID titleId, String hash) {}
    private record ReversibleSettlement(long amountMinor, UUID accountId, String direction, String description,
                                        UUID categoryId, String categoryDirection) {}
    private record ContactRoles(boolean customer, boolean supplier) {}
}
