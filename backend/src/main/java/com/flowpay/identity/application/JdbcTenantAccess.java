package com.flowpay.identity.application;

import com.flowpay.identity.api.TenantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JdbcTenantAccess implements TenantAccess {
    private final JdbcTemplate jdbc;

    public JdbcTenantAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public CurrentTenant requireSingleTenant(String email) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String requestedTenant = attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getHeader("X-FlowPay-Tenant") : null;
        if (requestedTenant != null && !requestedTenant.isBlank()) {
            java.util.UUID tenantId;
            try { tenantId = java.util.UUID.fromString(requestedTenant); }
            catch (IllegalArgumentException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A empresa selecionada é inválida.");
            }
            return findTenants(email, tenantId).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "A conta não tem acesso à empresa selecionada."));
        }
        var tenants = findTenants(email, null);
        if (tenants.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A conta não tem acesso a uma empresa.");
        if (tenants.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Selecione uma empresa para continuar.");
        return tenants.getFirst();
    }

    @Override
    public java.util.List<CurrentTenant> listTenants(String email) {
        return findTenants(email, null);
    }

    private java.util.List<CurrentTenant> findTenants(String email, java.util.UUID tenantId) {
        String filter = tenantId == null ? "" : " AND t.id = ?";
        var tenants = jdbc.query("""
                        SELECT t.id, t.name, m.role
                        FROM membership m
                        JOIN app_user u ON u.id = m.user_id
                        JOIN tenant t ON t.id = m.tenant_id
                        WHERE u.email = ?
                        """ + filter + """
                        ORDER BY t.created_at, t.id
                        """,
                (rs, row) -> new CurrentTenant(rs.getObject("id", java.util.UUID.class), rs.getString("name"), rs.getString("role")),
                tenantId == null ? new Object[]{email} : new Object[]{email, tenantId});
        return tenants;
    }

    @Override
    public java.util.UUID requireUserId(String email) {
        return jdbc.query("SELECT id FROM app_user WHERE email = ?",
                (rs, row) -> rs.getObject("id", java.util.UUID.class), email).stream().findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Identidade autenticada não encontrada."));
    }
}
