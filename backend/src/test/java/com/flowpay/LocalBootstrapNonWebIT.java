package com.flowpay;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class LocalBootstrapNonWebIT {

    @Test
    void localBootstrapCreatesItsOperatorWithoutServletDemoCleanupBeans() throws Exception {
        var postgres = PostgresApplicationIT.POSTGRES;
        String uniqueId = UUID.randomUUID().toString();
        String email = "local-bootstrap-" + uniqueId + "@example.test";
        String companyName = "FlowPay local bootstrap test " + uniqueId;
        ConfigurableApplicationContext context = null;

        try {
            context = new SpringApplicationBuilder(FlowPayApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("local-bootstrap")
                    .run(
                            "--spring.datasource.url=" + postgres.getJdbcUrl(),
                            "--spring.datasource.username=" + postgres.getUsername(),
                            "--spring.datasource.password=" + postgres.getPassword(),
                            "--FLOWPAY_BOOTSTRAP_ENABLED=true",
                            "--FLOWPAY_BOOTSTRAP_COMPANY_NAME=" + companyName,
                            "--FLOWPAY_BOOTSTRAP_EMAIL=" + email,
                            "--FLOWPAY_BOOTSTRAP_PASSWORD=test-" + uniqueId);

            assertThat(context.containsBean("demoCleanupScheduler")).isFalse();
            assertThat(context.containsBean("demoCleanupWorker")).isFalse();

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email=? AND demo=false", Long.class, email))
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM company c JOIN app_user u ON u.company_id=c.id
                    WHERE u.email=? AND c.name=? AND c.demo=false
                    """, Long.class, email, companyName)).isEqualTo(1L);
        } finally {
            if (context != null) context.close();
            cleanup(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), email, companyName);
        }
    }

    private static void cleanup(String url, String username, String password, String email, String companyName)
            throws SQLException {
        try (var connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                try (var schema = connection.prepareStatement("SELECT to_regclass('public.app_user')");
                        var result = schema.executeQuery()) {
                    if (!result.next() || result.getString(1) == null) {
                        connection.commit();
                        return;
                    }
                }
                UUID userId = null;
                UUID companyId = null;
                try (var query = connection.prepareStatement("""
                        SELECT u.id, u.company_id FROM app_user u
                        JOIN company c ON c.id=u.company_id
                        WHERE u.email=? AND u.demo=false AND c.demo=false AND c.name=?
                        """)) {
                    query.setString(1, email);
                    query.setString(2, companyName);
                    try (var rows = query.executeQuery()) {
                        if (rows.next()) {
                            userId = rows.getObject(1, UUID.class);
                            companyId = rows.getObject(2, UUID.class);
                            if (rows.next()) throw new IllegalStateException("Bootstrap test fixture identity is ambiguous.");
                        }
                    }
                }
                if (userId != null && companyId != null) {
                    deleteForUser(connection, "DELETE FROM spring_session WHERE principal_name=?", userId.toString());
                    deleteForCompany(connection, "DELETE FROM payment_event WHERE company_id=?", companyId);
                    deleteForCompany(connection, "DELETE FROM idempotency_record WHERE company_id=?", companyId);
                    deleteForCompany(connection, "DELETE FROM payment WHERE company_id=?", companyId);
                    deleteForCompany(connection, "DELETE FROM customer WHERE company_id=?", companyId);
                    try (var statement = connection.prepareStatement("DELETE FROM app_user WHERE id=? AND company_id=? AND email=? AND demo=false")) {
                        statement.setObject(1, userId);
                        statement.setObject(2, companyId);
                        statement.setString(3, email);
                        statement.executeUpdate();
                    }
                    try (var statement = connection.prepareStatement("DELETE FROM company WHERE id=? AND name=? AND demo=false")) {
                        statement.setObject(1, companyId);
                        statement.setString(2, companyName);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static void deleteForCompany(java.sql.Connection connection, String sql, UUID companyId) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, companyId);
            statement.executeUpdate();
        }
    }

    private static void deleteForUser(java.sql.Connection connection, String sql, String principalName) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, principalName);
            statement.executeUpdate();
        }
    }
}
