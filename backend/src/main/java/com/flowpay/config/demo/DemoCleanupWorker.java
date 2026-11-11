package com.flowpay.config.demo;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.CompanyProvisioning;
import com.flowpay.customer.CustomerOperations;
import com.flowpay.identity.IdentityProvisioning;
import com.flowpay.payment.PaymentOperations;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DemoCleanupWorker {
    private final CompanyAccess companies;
    private final CompanyProvisioning companyProvisioning;
    private final CustomerOperations customers;
    private final PaymentOperations payments;
    private final IdentityProvisioning identities;
    private final JdbcTemplate jdbc;
    private final SessionRepository<? extends Session> sessions;

    DemoCleanupWorker(CompanyAccess companies, CompanyProvisioning companyProvisioning,
            CustomerOperations customers, PaymentOperations payments,
            IdentityProvisioning identities, JdbcTemplate jdbc,
            SessionRepository<? extends Session> sessions) {
        this.companies = companies;
        this.companyProvisioning = companyProvisioning;
        this.customers = customers;
        this.payments = payments;
        this.identities = identities;
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    @Transactional
    public void cleanOne(UUID companyId, Instant cutoff) {
        if (companies.lockExpiredDemo(companyId, cutoff).isEmpty()) return;
        List<String> sessionIds = persistedSessionIds(companyId, cutoff);
        payments.deleteExpiredDemoPayments(companyId, cutoff);
        customers.deleteExpiredDemoCustomers(companyId, cutoff);
        identities.deleteExpiredDemoOperators(companyId, cutoff);
        companies.lockExpiredDemo(companyId, cutoff)
                .orElseThrow(() -> new IllegalStateException("Session cleanup requires a locked expired demo."));
        for (String sessionId : sessionIds) sessions.deleteById(sessionId);
        companyProvisioning.deleteExpiredDemo(companyId, cutoff);
    }

    private List<String> persistedSessionIds(UUID companyId, Instant cutoff) {
        companies.lockExpiredDemo(companyId, cutoff)
                .orElseThrow(() -> new IllegalStateException("Session cleanup requires a locked expired demo."));
        return jdbc.query("""
                SELECT session_id FROM spring_session
                WHERE principal_name IN (
                    SELECT id::text FROM app_user WHERE company_id=? AND demo
                )
                ORDER BY session_id
                """, (row, index) -> row.getString(1).trim(), companyId);
    }
}
