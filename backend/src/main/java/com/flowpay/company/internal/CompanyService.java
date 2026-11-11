package com.flowpay.company.internal;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.CompanyProvisioning;
import com.flowpay.company.CompanyUnavailableException;
import com.flowpay.company.CompanyView;
import com.flowpay.company.ContractValidationException;
import com.flowpay.company.NewCompany;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
class CompanyService implements CompanyAccess, CompanyProvisioning {
    private static final Pattern OUTER_WHITESPACE = Pattern.compile("\\A\\s+|\\s+\\z", Pattern.UNICODE_CHARACTER_CLASS);
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    CompanyService(EntityManager entities, JdbcTemplate jdbc, Clock clock) {
        this.entities = entities;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyView requireActive(UUID companyId) {
        return active(entities.find(CompanyEntity.class, companyId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompanyView lockActive(UUID companyId) {
        return active(locked(companyId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompanyView lockForCleanup(UUID companyId) {
        return existing(locked(companyId)).view();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CompanyView> lockExpiredDemo(UUID companyId, Instant cutoff) {
        CompanyEntity company = locked(companyId);
        if (company == null || !company.demo || company.expiresAt == null || company.expiresAt.isAfter(cutoff)) {
            return Optional.empty();
        }
        return Optional.of(company.view());
    }

    private CompanyEntity locked(UUID id) {
        CompanyEntity company = entities.find(CompanyEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        // Refresh even if this transaction already held a pre-lock snapshot in its persistence context.
        if (company != null) entities.refresh(company, LockModeType.PESSIMISTIC_WRITE);
        return company;
    }

    private CompanyEntity existing(CompanyEntity company) {
        if (company == null) throw new CompanyUnavailableException(CompanyUnavailableException.Reason.MISSING);
        return company;
    }

    private CompanyView active(CompanyEntity company) {
        existing(company);
        if (company.demo && !company.expiresAt.isAfter(clock.instant())) {
            throw new CompanyUnavailableException(CompanyUnavailableException.Reason.EXPIRED_DEMO);
        }
        return company.view();
    }

    @Override
    @Transactional
    public CompanyView create(NewCompany command) {
        String name = command.name() == null ? "" : OUTER_WHITESPACE.matcher(command.name()).replaceAll("");
        if (name.isEmpty()) throw new ContractValidationException("name", "REQUIRED", "Name is required.");
        if (name.codePointCount(0, name.length()) > 120) {
            throw new ContractValidationException("name", "TOO_LONG", "Name must contain at most 120 characters.");
        }
        // PostgreSQL TIMESTAMPTZ stores microseconds; align the single sampled instant to
        // that precision so persisted createdAt and expiresAt retain an exact 24-hour gap.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var expiry = command.demo() ? now.plus(Duration.ofHours(24)) : null;
        var company = new CompanyEntity(UUID.randomUUID(), name, now, command.demo(), expiry);
        entities.persist(company);
        return company.view();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CompanyView> createDemoIfAvailable(int activeCapacity) {
        if (activeCapacity < 1) throw new IllegalArgumentException("Active demo capacity must be positive.");
        // Serialize only demo admission. The lock is transaction-scoped, so the caller's outer
        // transaction keeps the reserved slot through fixture seeding or rolls it back on failure.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(741852963) IS NULL", Boolean.class);
        long active = jdbc.queryForObject("SELECT count(*) FROM company WHERE demo AND expires_at > ?",
                Long.class, Timestamp.from(clock.instant()));
        if (active >= activeCapacity) return Optional.empty();
        return Optional.of(create(new NewCompany("FlowPay Demo Workspace", true)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> expiredDemoIds(Instant cutoff, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("Cleanup batch must be between 1 and 500.");
        return jdbc.query("""
                SELECT id FROM company WHERE demo AND expires_at <= ?
                ORDER BY expires_at,id LIMIT ?
                """, (row, index) -> row.getObject(1, UUID.class), Timestamp.from(cutoff), limit);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteExpiredDemo(UUID companyId, Instant cutoff) {
        var company = locked(companyId);
        if (company == null || !company.demo || company.expiresAt == null || company.expiresAt.isAfter(cutoff)) {
            throw new IllegalStateException("Only a locked expired demo can be deleted.");
        }
        entities.remove(company);
    }
}
