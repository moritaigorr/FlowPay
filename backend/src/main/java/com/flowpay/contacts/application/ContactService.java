package com.flowpay.contacts.application;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.identity.api.TenantRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContactService {
    private final JdbcTemplate jdbc;
    private final TenantAccess tenantAccess;

    public ContactService(JdbcTemplate jdbc, TenantAccess tenantAccess) {
        this.jdbc = jdbc;
        this.tenantAccess = tenantAccess;
    }

    @Transactional(readOnly = true)
    public ContactPage search(String email, String query, int page, int size) {
        validatePage(page, size);
        var tenant = tenantAccess.requireSingleTenant(email);
        var normalizedQuery = normalize(query);
        var filter = normalizedQuery == null ? "" : " AND (name ILIKE ? OR email ILIKE ? OR phone ILIKE ?)";
        var total = normalizedQuery == null
                ? jdbc.queryForObject("SELECT count(*) FROM contact WHERE tenant_id = ?", Long.class, tenant.id())
                : jdbc.queryForObject("SELECT count(*) FROM contact WHERE tenant_id = ?" + filter,
                        Long.class, tenant.id(), "%" + normalizedQuery + "%", "%" + normalizedQuery + "%", "%" + normalizedQuery + "%");
        var select = """
                SELECT id, name, is_customer, is_supplier, email, phone, notes, created_at, updated_at
                FROM contact WHERE tenant_id =
                """;
        var orderAndPage = " ORDER BY lower(name), id LIMIT ? OFFSET ?";
        var items = normalizedQuery == null
                ? jdbc.query(select + "?" + orderAndPage, ContactService::mapContact, tenant.id(), size, page * size)
                : jdbc.query(select + "?" + filter + orderAndPage, ContactService::mapContact,
                        tenant.id(), "%" + normalizedQuery + "%", "%" + normalizedQuery + "%", "%" + normalizedQuery + "%", size, page * size);
        return new ContactPage(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public ContactView get(String email, UUID id) {
        var tenant = tenantAccess.requireSingleTenant(email);
        return get(tenant.id(), id);
    }

    @Transactional
    public ContactView create(String email, ContactInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var id = UUID.randomUUID();
        var roles = input.roles();
        jdbc.update("""
                INSERT INTO contact (id, tenant_id, name, is_customer, is_supplier, email, phone, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenant.id(), input.name().trim(), roles.contains(ContactRole.CUSTOMER),
                roles.contains(ContactRole.SUPPLIER), normalize(input.email()), normalize(input.phone()), normalize(input.notes()));
        return get(tenant.id(), id);
    }

    @Transactional
    public ContactView update(String email, UUID id, ContactInput input) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireBusinessWriter(tenant.role());
        var roles = input.roles();
        var changed = jdbc.update("""
                UPDATE contact
                SET name = ?, is_customer = ?, is_supplier = ?, email = ?, phone = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND id = ?
                """, input.name().trim(), roles.contains(ContactRole.CUSTOMER), roles.contains(ContactRole.SUPPLIER),
                normalize(input.email()), normalize(input.phone()), normalize(input.notes()), tenant.id(), id);
        if (changed == 0) throw notFound();
        return get(tenant.id(), id);
    }

    private ContactView get(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT id, name, is_customer, is_supplier, email, phone, notes, created_at, updated_at
                FROM contact WHERE tenant_id = ? AND id = ?
                """, ContactService::mapContact, tenantId, id)
                .stream().findFirst().orElseThrow(ContactService::notFound);
    }

    private static ContactView mapContact(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ContactView(rs.getObject("id", UUID.class), rs.getString("name"),
                roles(rs.getBoolean("is_customer"), rs.getBoolean("is_supplier")),
                rs.getString("email"), rs.getString("phone"), rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static Set<ContactRole> roles(boolean customer, boolean supplier) {
        var result = new java.util.HashSet<ContactRole>();
        if (customer) result.add(ContactRole.CUSTOMER);
        if (supplier) result.add(ContactRole.SUPPLIER);
        return Set.copyOf(result);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho fora dos limites permitidos.");
        }
    }

    private static void requireBusinessWriter(String role) {
        if (!TenantRole.canManageBusinessData(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite alterar contatos.");
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Contato não encontrado.");
    }

    public enum ContactRole { CUSTOMER, SUPPLIER }
    public record ContactInput(String name, Set<ContactRole> roles, String email, String phone, String notes) {}
    public record ContactView(UUID id, String name, Set<ContactRole> roles, String email, String phone,
                              String notes, java.time.Instant createdAt, java.time.Instant updatedAt) {
        @Override public String toString() { return "ContactView[REDACTED]"; }
    }
    public record ContactPage(List<ContactView> items, long total, int page, int size) {}
}
