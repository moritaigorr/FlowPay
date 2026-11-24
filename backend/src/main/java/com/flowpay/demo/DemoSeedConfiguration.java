package com.flowpay.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Synthetic, repeatable data for an explicitly selected local demo profile. */
@Configuration
@Profile("demo")
class DemoSeedConfiguration {

    private static final UUID USER_ID = UUID.fromString("df532c38-c914-4f62-9341-41d20f3c9021");
    private static final UUID TENANT_ID = UUID.fromString("db702708-27ec-47d1-8e06-1f9587feafcb");
    private static final UUID E2E_READER_TENANT_ID = UUID.fromString("5817d87b-ae9b-4cdf-a4c7-2a6bd8d66745");
    private static final UUID CUSTOMER_ID = UUID.fromString("5a602c54-6224-4ed5-9cb5-127922ba54a1");
    private static final UUID SUPPLIER_ID = UUID.fromString("1652d7ef-e311-42e2-9cb2-63db9eea7ce2");
    private static final UUID ACCOUNT_ID = UUID.fromString("ba05801e-75f1-477b-b4ae-16006c473202");
    private static final UUID INFLOW_CATEGORY_ID = UUID.fromString("8e3590f0-04f0-45a0-9ef1-86786fe6b537");
    private static final UUID OUTFLOW_CATEGORY_ID = UUID.fromString("33e37ac2-e8c6-48b3-8981-0192ac33e347");
    private static final UUID RECEIVABLE_ID = UUID.fromString("f2ff39cb-64c2-4650-9039-a687fa143c39");
    private static final UUID PAYABLE_ID = UUID.fromString("95018485-1c79-4085-bc83-46503420884d");

    @Bean
    ApplicationRunner seedDemoData(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                   TransactionTemplate transactions,
                                   Environment environment,
                                   @Value("${spring.datasource.url}") String datasourceUrl,
                                   @Value("${FLOWPAY_DEMO_PASSWORD:}") String demoPassword,
                                   @Value("${FLOWPAY_DEMO_MULTI_TENANT_E2E:false}") boolean seedE2eReaderTenant) {
        return args -> {
            requireLocalDemoDatasource(environment, datasourceUrl);
            requireDemoPassword(demoPassword);
            transactions.executeWithoutResult(status -> {
                seed(jdbc, passwordEncoder, demoPassword);
                if (seedE2eReaderTenant) seedE2eReaderTenant(jdbc);
            });
        };
    }

    private static void requireLocalDemoDatasource(Environment environment, String datasourceUrl) {
        boolean localProfile = environment.acceptsProfiles(Profiles.of("local"));
        boolean loopbackFlowPayDatabase = datasourceUrl.matches(
                "(?i)^jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::[0-9]+)?/flowpay(?:\\?.*)?$");
        if (!localProfile || !loopbackFlowPayDatabase) {
            throw new IllegalStateException(
                    "The demo seed requires the local profile and a loopback PostgreSQL database named flowpay.");
        }
    }

    private static void requireDemoPassword(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.isBlank() || password.length() < 12 || length > 72) {
            throw new IllegalStateException("FLOWPAY_DEMO_PASSWORD must contain 12 to 72 UTF-8 bytes when the demo profile is active.");
        }
    }

    private static void seed(JdbcTemplate jdbc, PasswordEncoder passwords, String demoPassword) {
        jdbc.update("""
                INSERT INTO app_user(id, email, password_hash) VALUES (?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash
                WHERE app_user.email = EXCLUDED.email
                """, USER_ID, "demo@flowpay.local", passwords.encode(demoPassword));
        requireOwnedRow(jdbc, "SELECT count(*) FROM app_user WHERE id = ? AND email = ?", USER_ID,
                "demo@flowpay.local",
                "demo user identifier is already used by another account");

        jdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                TENANT_ID, "FlowPay Demonstração");
        requireOwnedRow(jdbc, "SELECT count(*) FROM tenant WHERE id = ? AND name = ?", TENANT_ID,
                "FlowPay Demonstração",
                "demo tenant identifier is already used by another company");
        jdbc.update("""
                INSERT INTO membership(tenant_id, user_id, role) VALUES (?, ?, 'OWNER')
                ON CONFLICT (tenant_id, user_id) DO NOTHING
                """, TENANT_ID, USER_ID);

        jdbc.update("""
                INSERT INTO contact(id, tenant_id, name, is_customer, is_supplier, email)
                VALUES (?, ?, 'Cliente Exemplo Ltda.', TRUE, FALSE, 'financeiro@cliente-exemplo.invalid')
                ON CONFLICT (id) DO NOTHING
                """, CUSTOMER_ID, TENANT_ID);
        jdbc.update("""
                INSERT INTO contact(id, tenant_id, name, is_customer, is_supplier, email)
                VALUES (?, ?, 'Fornecedor Exemplo Ltda.', FALSE, TRUE, 'contato@fornecedor-exemplo.invalid')
                ON CONFLICT (id) DO NOTHING
                """, SUPPLIER_ID, TENANT_ID);
        jdbc.update("""
                INSERT INTO financial_account(id, tenant_id, name, account_type, opening_balance_date,
                    creation_key, creation_payload_hash)
                VALUES (?, ?, 'Conta principal demo', 'BANK', DATE '2026-11-01', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, ACCOUNT_ID, TENANT_ID, UUID.fromString("609b9dc1-2d0b-4435-ae1b-928f68778509"), hash("account-v1"));
        jdbc.update("""
                INSERT INTO cash_transaction(id, tenant_id, financial_account_id, direction, amount_minor,
                    effective_date, origin, description)
                VALUES (?, ?, ?, 'INFLOW', 2500000, DATE '2026-11-01', 'OPENING_BALANCE', 'Saldo inicial fictício')
                ON CONFLICT (id) DO NOTHING
                """, UUID.fromString("494a24c9-c4d6-4702-bcc5-7da360cd62a1"), TENANT_ID, ACCOUNT_ID);

        jdbc.update("""
                INSERT INTO financial_category(id, tenant_id, name, direction)
                VALUES (?, ?, 'Serviços', 'INFLOW') ON CONFLICT (id) DO NOTHING
                """, INFLOW_CATEGORY_ID, TENANT_ID);
        jdbc.update("""
                INSERT INTO financial_category(id, tenant_id, name, direction)
                VALUES (?, ?, 'Despesas operacionais', 'OUTFLOW') ON CONFLICT (id) DO NOTHING
                """, OUTFLOW_CATEGORY_ID, TENANT_ID);

        jdbc.update("""
                INSERT INTO financial_title(id, tenant_id, kind, description, contact_id, category_id,
                    category_direction, planned_account_id, original_amount_minor, due_date, creation_key,
                    creation_payload_hash)
                VALUES (?, ?, 'RECEIVABLE', 'Projeto demonstração', ?, ?, 'INFLOW', ?, 185000,
                    DATE '2026-11-30', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, RECEIVABLE_ID, TENANT_ID, CUSTOMER_ID, INFLOW_CATEGORY_ID, ACCOUNT_ID,
                UUID.fromString("b4b3592c-66bf-423a-b1b6-81cc43cf84a4"), hash("receivable-v1"));
        jdbc.update("""
                INSERT INTO financial_title(id, tenant_id, kind, description, contact_id, category_id,
                    category_direction, planned_account_id, original_amount_minor, due_date, creation_key,
                    creation_payload_hash)
                VALUES (?, ?, 'PAYABLE', 'Licença de software', ?, ?, 'OUTFLOW', ?, 78000,
                    DATE '2026-11-20', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, PAYABLE_ID, TENANT_ID, SUPPLIER_ID, OUTFLOW_CATEGORY_ID, ACCOUNT_ID,
                UUID.fromString("fbe4937a-57c9-4798-8654-9b6d1ea37107"), hash("payable-v1"));

        requireTenantRows(jdbc);
    }

    private static void seedE2eReaderTenant(JdbcTemplate jdbc) {
        String tenantName = "FlowPay Leitura E2E";
        jdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                E2E_READER_TENANT_ID, tenantName);
        requireOwnedRow(jdbc, "SELECT count(*) FROM tenant WHERE id = ? AND name = ?", E2E_READER_TENANT_ID,
                tenantName, "demo E2E tenant identifier is already used by another company");
        jdbc.update("""
                INSERT INTO membership(tenant_id, user_id, role) VALUES (?, ?, 'READER')
                ON CONFLICT (tenant_id, user_id) DO NOTHING
                """, E2E_READER_TENANT_ID, USER_ID);
        Long membershipCount = jdbc.queryForObject("""
                SELECT count(*) FROM membership
                WHERE tenant_id = ? AND user_id = ? AND role = 'READER'
                """, Long.class, E2E_READER_TENANT_ID, USER_ID);
        if (membershipCount == null || membershipCount != 1) {
            throw new IllegalStateException("The demo E2E tenant membership conflicts with data outside the fixture.");
        }
    }

    private static void requireOwnedRow(JdbcTemplate jdbc, String query, UUID id, String value, String message) {
        if (jdbc.queryForObject(query, Long.class, id, value) != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireTenantRows(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM contact WHERE tenant_id = ? AND ((id = ? AND name = 'Cliente Exemplo Ltda.')
                              OR (id = ? AND name = 'Fornecedor Exemplo Ltda.')))
                     + (SELECT count(*) FROM financial_account WHERE tenant_id = ? AND id = ? AND name = 'Conta principal demo')
                     + (SELECT count(*) FROM financial_category WHERE tenant_id = ? AND ((id = ? AND name = 'Serviços' AND direction = 'INFLOW')
                              OR (id = ? AND name = 'Despesas operacionais' AND direction = 'OUTFLOW')))
                     + (SELECT count(*) FROM financial_title WHERE tenant_id = ? AND ((id = ? AND description = 'Projeto demonstração' AND original_amount_minor = 185000)
                              OR (id = ? AND description = 'Licença de software' AND original_amount_minor = 78000)))
                     + (SELECT count(*) FROM membership WHERE tenant_id = ? AND user_id = ? AND role = 'OWNER')
                     + (SELECT count(*) FROM cash_transaction WHERE tenant_id = ? AND id = ? AND origin = 'OPENING_BALANCE'
                              AND financial_account_id = ? AND amount_minor = 2500000)
                """, Long.class, TENANT_ID, CUSTOMER_ID, SUPPLIER_ID, TENANT_ID, ACCOUNT_ID,
                TENANT_ID, INFLOW_CATEGORY_ID, OUTFLOW_CATEGORY_ID, TENANT_ID, RECEIVABLE_ID, PAYABLE_ID,
                TENANT_ID, USER_ID, TENANT_ID, UUID.fromString("494a24c9-c4d6-4702-bcc5-7da360cd62a1"), ACCOUNT_ID);
        if (count == null || count != 9) {
            throw new IllegalStateException("A demo seed identifier conflicts with data outside the demo tenant.");
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
