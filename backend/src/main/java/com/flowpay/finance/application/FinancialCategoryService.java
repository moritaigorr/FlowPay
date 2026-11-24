package com.flowpay.finance.application;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.identity.api.TenantRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialCategoryService {
    private final JdbcTemplate jdbc;
    private final TenantAccess tenantAccess;

    public FinancialCategoryService(JdbcTemplate jdbc, TenantAccess tenantAccess) {
        this.jdbc = jdbc;
        this.tenantAccess = tenantAccess;
    }

    @Transactional(readOnly = true)
    public CategoryPage list(String email, CategoryDirection direction, int page, int size) {
        validatePage(page, size);
        var tenant = tenantAccess.requireSingleTenant(email);
        var filter = direction == null ? "" : " AND direction = ?";
        var total = direction == null
                ? jdbc.queryForObject("SELECT count(*) FROM financial_category WHERE tenant_id = ?", Long.class, tenant.id())
                : jdbc.queryForObject("SELECT count(*) FROM financial_category WHERE tenant_id = ?" + filter, Long.class, tenant.id(), direction.name());
        var sql = "SELECT id, name, direction, status, created_at, updated_at FROM financial_category WHERE tenant_id = ?" + filter
                + " ORDER BY direction, lower(name), id LIMIT ? OFFSET ?";
        var items = direction == null
                ? jdbc.query(sql, FinancialCategoryService::map, tenant.id(), size, page * size)
                : jdbc.query(sql, FinancialCategoryService::map, tenant.id(), direction.name(), size, page * size);
        return new CategoryPage(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public CategoryView get(String email, UUID id) {
        var tenant = tenantAccess.requireSingleTenant(email);
        return get(tenant.id(), id);
    }

    @Transactional
    public CategoryView create(String email, String name, CategoryDirection direction) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var id = UUID.randomUUID();
        lockCategoryWrites(tenant.id(), direction);
        if (nameExists(tenant.id(), direction, name.trim(), null)) throw conflict();
        jdbc.update("INSERT INTO financial_category (id, tenant_id, name, direction) VALUES (?, ?, ?, ?)",
                id, tenant.id(), name.trim(), direction.name());
        return get(tenant.id(), id);
    }

    @Transactional
    public CategoryView update(String email, UUID id, String name, CategoryStatus status) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var direction = jdbc.query("SELECT direction FROM financial_category WHERE tenant_id = ? AND id = ?",
                (rs, row) -> CategoryDirection.valueOf(rs.getString("direction")), tenant.id(), id)
                .stream().findFirst().orElseThrow(FinancialCategoryService::notFound);
        lockCategoryWrites(tenant.id(), direction);
        if (nameExists(tenant.id(), direction, name.trim(), id)) throw conflict();
        jdbc.update("""
                UPDATE financial_category SET name = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ?
                """, name.trim(), status.name(), tenant.id(), id);
        return get(tenant.id(), id);
    }

    private void lockCategoryWrites(UUID tenantId, CategoryDirection direction) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", (rs, row) -> null, tenantId + ":" + direction.name());
    }

    private boolean nameExists(UUID tenantId, CategoryDirection direction, String name, UUID exceptId) {
        var sql = "SELECT EXISTS (SELECT 1 FROM financial_category WHERE tenant_id = ? AND direction = ? AND lower(name) = lower(?)"
                + (exceptId == null ? ")" : " AND id <> ?)");
        return exceptId == null
                ? Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, tenantId, direction.name(), name))
                : Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, tenantId, direction.name(), name, exceptId));
    }

    private CategoryView get(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT id, name, direction, status, created_at, updated_at FROM financial_category
                WHERE tenant_id = ? AND id = ?
                """, FinancialCategoryService::map, tenantId, id).stream().findFirst().orElseThrow(FinancialCategoryService::notFound);
    }

    private static CategoryView map(ResultSet rs, int row) throws SQLException {
        return new CategoryView(rs.getObject("id", UUID.class), rs.getString("name"),
                CategoryDirection.valueOf(rs.getString("direction")), CategoryStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho fora dos limites permitidos.");
        }
    }

    private static void requireBusinessWriter(String role) {
        if (!TenantRole.canManageBusinessData(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite alterar categorias.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada.");
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma categoria com esse nome e direção.");
    }

    public enum CategoryDirection { INFLOW, OUTFLOW }
    public enum CategoryStatus { ACTIVE, ARCHIVED }
    public record CategoryView(UUID id, String name, CategoryDirection direction, CategoryStatus status, Instant createdAt, Instant updatedAt) {
        @Override public String toString() { return "CategoryView[REDACTED]"; }
    }
    public record CategoryPage(List<CategoryView> items, long total, int page, int size) {}
}
