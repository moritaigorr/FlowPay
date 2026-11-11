package com.flowpay;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.CompanyUnavailableException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyAccessIT extends PostgresApplicationIT {
    @Autowired CompanyAccess companies;
    @Autowired com.flowpay.company.CompanyProvisioning provisioning;
    @Autowired com.flowpay.identity.IdentityProvisioning identityProvisioning;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    UUID company(boolean demo, Instant expiry) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO company(id,name,demo,expires_at) VALUES (?,?,?,?)", id, "Synthetic company", demo,
                expiry == null ? null : java.sql.Timestamp.from(expiry));
        return id;
    }

    @Test
    void provisioningOwnsTheCreationTimeAndTwentyFourHourDemoExpiry() {
        var demo = provisioning.create(new com.flowpay.company.NewCompany("\u00a0Synthetic demo\u00a0", true));
        assertThat(demo.name()).isEqualTo("Synthetic demo");
        assertThat(demo.expiresAt()).isEqualTo(demo.createdAt().plusSeconds(86_400));
        assertThat(companies.requireActive(demo.id()).demo()).isTrue();
        var conventional = provisioning.create(new com.flowpay.company.NewCompany("Synthetic conventional", false));
        assertThat(conventional.expiresAt()).isNull();
    }

    @Test
    void unicodeWhitespaceOnlyCompanyNameIsRejected() {
        assertThatThrownBy(() -> provisioning.create(new com.flowpay.company.NewCompany("\u00a0\u2003", false)))
                .isInstanceOf(com.flowpay.company.ContractValidationException.class);
    }

    @Test
    void demoProvisioningCreatesAPasswordlessOperatorThroughThePublicFacades() {
        var demo = provisioning.create(new com.flowpay.company.NewCompany("Synthetic demo", true));
        var operator = identityProvisioning.create(new com.flowpay.identity.NewOperator(demo.id(),
                UUID.randomUUID() + "@example.test", null, true));
        assertThat(operator.companyId()).isEqualTo(demo.id());
        assertThat(jdbc.queryForObject("SELECT demo FROM app_user WHERE id=?", Boolean.class, operator.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, operator.id())).isNull();
    }

    @Test
    void activeSnapshotRejectsMissingAndExpiredCompanies() {
        UUID id = company(false, null);
        assertThat(companies.requireActive(id).id()).isEqualTo(id);
        assertThatThrownBy(() -> companies.requireActive(UUID.randomUUID()))
                .isInstanceOf(CompanyUnavailableException.class);
        assertThatThrownBy(() -> companies.requireActive(company(true, Instant.now().minusSeconds(1))))
                .isInstanceOf(CompanyUnavailableException.class);
    }

    @Test
    void lockingRequiresAnExistingTransactionAndCleanupCanReadExpiredDemo() {
        UUID id = company(true, Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> companies.lockActive(id)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> companies.lockForCleanup(id)).isInstanceOf(IllegalTransactionStateException.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(companies.lockForCleanup(id).demo()).isTrue();
            assertThatThrownBy(() -> companies.lockActive(id)).isInstanceOf(CompanyUnavailableException.class);
            status.setRollbackOnly();
        });
    }

    @Test
    void activeLockRevalidatesExpiryAfterWaitingForTheRow() throws Exception {
        UUID id = company(true, Instant.now().plusSeconds(3600));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var owner = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            companies.lockForCleanup(id);
            jdbc.update("UPDATE company SET expires_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), id);
            locked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Lock release timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var waiter = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            companies.requireActive(id);
            companies.lockActive(id);
        }));
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                waiting = jdbc.queryForObject("SELECT count(*) > 0 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query ILIKE '%company%'",
                        Boolean.class);
                if (waiting) break;
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertThat(waiting).as("Second PostgreSQL connection waits for the company row lock").isTrue();
        } finally {
            release.countDown();
        }
        owner.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> waiter.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(CompanyUnavailableException.class);
    }
}
