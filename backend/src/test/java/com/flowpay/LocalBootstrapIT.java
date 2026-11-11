package com.flowpay;

import com.flowpay.config.bootstrap.LocalBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalBootstrapIT extends PostgresApplicationIT {
    @Autowired LocalBootstrap bootstrap;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    MockEnvironment environment(String email) {
        return new MockEnvironment().withProperty("FLOWPAY_BOOTSTRAP_ENABLED", "true")
                .withProperty("FLOWPAY_BOOTSTRAP_COMPANY_NAME", "\u2003Local company\u2003")
                .withProperty("FLOWPAY_BOOTSTRAP_EMAIL", email)
                .withProperty("FLOWPAY_BOOTSTRAP_PASSWORD", "  local 🔒 password  ");
    }

    @Test
    void createsBothRowsAndNeverResetsOrAdoptsAnExistingOperator() {
        String email = UUID.randomUUID() + "@example.test";
        var environment = environment(email.toUpperCase(java.util.Locale.ROOT));
        environment.setActiveProfiles("local-bootstrap");
        bootstrap.provision(environment);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE email=?", String.class, email);
        assertThat(hash).startsWith("{pbkdf2@SpringSecurity_v5_8}");
        assertThat(passwords.matches("  local 🔒 password  ", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT c.name FROM company c JOIN app_user u ON u.company_id=c.id WHERE u.email=?", String.class, email))
                .isEqualTo("Local company");
        long companies = jdbc.queryForObject("SELECT count(*) FROM company", Long.class);
        assertThatThrownBy(() -> bootstrap.provision(environment)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company", Long.class)).isEqualTo(companies);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE email=?", String.class, email)).isEqualTo(hash);
    }

    @Test
    void rejectsProductionPublicAndImplicitProvisioningBeforeAnyWrite() {
        long companies = jdbc.queryForObject("SELECT count(*) FROM company", Long.class);
        for (String[] profiles : new String[][]{{}, {"local"}, {"local-bootstrap", "prod"}, {"local-bootstrap", "public"}}) {
            var environment = environment(UUID.randomUUID() + "@example.test");
            environment.setActiveProfiles(profiles);
            assertThatThrownBy(() -> bootstrap.provision(environment)).isInstanceOf(IllegalStateException.class);
        }
        var disabled = environment(UUID.randomUUID() + "@example.test").withProperty("FLOWPAY_BOOTSTRAP_ENABLED", "false");
        disabled.setActiveProfiles("local-bootstrap");
        assertThatThrownBy(() -> bootstrap.provision(disabled)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company", Long.class)).isEqualTo(companies);
    }

    @Test
    void invalidOperatorRollsBackNewCompany() {
        var environment = environment("invalid-email");
        environment.setActiveProfiles("local-bootstrap");
        long companies = jdbc.queryForObject("SELECT count(*) FROM company", Long.class);
        assertThatThrownBy(() -> bootstrap.provision(environment)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company", Long.class)).isEqualTo(companies);
    }

    @Test
    void simultaneousProvisioningOfTheSameEmailCreatesExactlyOneCompanyAndOperator() throws Exception {
        var environment = environment(UUID.randomUUID() + "@example.test");
        environment.setActiveProfiles("local-bootstrap");
        long before = jdbc.queryForObject("SELECT count(*) FROM company", Long.class);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt = () -> {
            try {
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                bootstrap.provision(environment);
                return true;
            } catch (IllegalStateException exception) {
                return false;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        };
        var first = java.util.concurrent.CompletableFuture.supplyAsync(attempt);
        var second = java.util.concurrent.CompletableFuture.supplyAsync(attempt);
        start.countDown();
        assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company", Long.class)).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email=?", Long.class,
                environment.getProperty("FLOWPAY_BOOTSTRAP_EMAIL"))).isEqualTo(1L);
    }
}
