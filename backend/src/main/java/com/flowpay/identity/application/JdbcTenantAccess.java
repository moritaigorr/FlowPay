package com.flowpay.identity.application;

import com.flowpay.identity.api.TenantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JdbcTenantAccess implements TenantAccess {
    private final JdbcTemplate jdbc;

    public JdbcTenantAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public CurrentTenant requireSingleTenant(String email) {
        var tenants = jdbc.query("""
                        SELECT t.id, t.name, m.role
                        FROM membership m
                        JOIN app_user u ON u.id = m.user_id
                        JOIN tenant t ON t.id = m.tenant_id
                        WHERE u.email = ?
                        ORDER BY t.created_at, t.id
                        LIMIT 2
                        """,
                (rs, row) -> new CurrentTenant(rs.getObject("id", java.util.UUID.class), rs.getString("name"), rs.getString("role")),
                email);
        if (tenants.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A conta não tem acesso a uma empresa.");
        if (tenants.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "A seleção de empresa ainda não está disponível.");
        return tenants.getFirst();
    }

    @Override
    public java.util.UUID requireUserId(String email) {
        return jdbc.query("SELECT id FROM app_user WHERE email = ?",
                (rs, row) -> rs.getObject("id", java.util.UUID.class), email).stream().findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Identidade autenticada não encontrada."));
    }
}
