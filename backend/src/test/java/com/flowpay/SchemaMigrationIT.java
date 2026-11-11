package com.flowpay;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaMigrationIT extends PostgresApplicationIT {
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SessionRepository<?> sessions;

    UUID company = UUID.randomUUID();
    UUID foreign = UUID.randomUUID();
    UUID customer = UUID.randomUUID();

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO company(id,name) VALUES (?, 'Owner'), (?, 'Foreign')", company, foreign);
        jdbc.update("INSERT INTO customer(id,company_id,name,email) VALUES (?,?, 'Customer','synthetic@example.test')", customer, company);
    }
    void payment(UUID id, UUID tenant, String amount, String currency, String status, String scenario) {
        jdbc.update("INSERT INTO payment(id,company_id,customer_id,amount,currency,status,simulation_scenario) VALUES (?,?,?,?,?,?,?)", id,tenant, customer, new java.math.BigDecimal(amount),currency,status,scenario);
    }
    @Test
    void amountCurrencyAndEnumsAreConstrained() {
        payment(UUID.randomUUID(), company, "99999999999999999.99", "BRL", "PENDING", "APPROVE");
        for (String amount : new String[]{"0", "-1", "100000000000000000.00"})
            assertThatThrownBy(() -> payment(UUID.randomUUID(), company, amount, "BRL", "PENDING", "APPROVE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payment(UUID.randomUUID(), company, "1", "USD", "PENDING", "APPROVE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payment(UUID.randomUUID(), company, "1", "BRL", "UNKNOWN", "APPROVE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payment(UUID.randomUUID(), company, "1", "BRL", "PENDING", "UNKNOWN")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void customerCannotBeReferencedByAnotherCompany() {
        assertThatThrownBy(() -> payment(UUID.randomUUID(), foreign, "1", "BRL", "PENDING", "APPROVE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonFiniteMoneyCannotEnterPostgresNumericColumn() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment(id,company_id,customer_id,amount,currency,status,simulation_scenario)
                VALUES (?,?,?,'NaN'::numeric,'BRL','PENDING','APPROVE')
                """, UUID.randomUUID(), company, customer))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void idempotencyKeyIsUniqueWithinCompanyAndOperation() {
        String sql = "INSERT INTO idempotency_record(id,company_id,operation,key,request_hash) VALUES (?,?,'create-payment','same',?)";
        jdbc.update(sql, UUID.randomUUID(),  company, "a".repeat(64));
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(),  company, "a".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(sql, UUID.randomUUID(),  foreign, "a".repeat(64));
    }
    @Test
    void eventsAndResponseCannotReferenceForeignPaymentOrActor() {
        UUID payment = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        payment(payment, company, "1", "BRL", "PENDING", "APPROVE");
        jdbc.update("INSERT INTO app_user(id,company_id,email,password_hash,demo) VALUES (?,?,?,'encoded',false)",
                actor, company, actor + "@example.test");
        String sql = "INSERT INTO payment_event(id,company_id,payment_id,sequence,new_status,actor_user_id) VALUES (?,?,?,1,'PENDING',?)";
        jdbc.update(sql, UUID.randomUUID(),  company, payment, actor);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(),  company, payment, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(),  foreign, payment, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO idempotency_record(id,company_id,operation,key,request_hash,payment_id,http_status,response_json) VALUES (?,?,'create-payment','foreign',?,?,201,'{}')", UUID.randomUUID(),  foreign, "a".repeat(64),payment)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test
    void supportedSessionRepositoryPersistsAttributesAndDeletesThem() {
        verifySession(sessions);
    }
    private <S extends Session> void verifySession(SessionRepository<S> repository) {
        S session = repository.createSession();
        session.setAttribute("synthetic", "value");
        repository.save(session);
        assertThat(repository.findById(session.getId()).<String>getAttribute("synthetic")).isEqualTo("value");
        repository.deleteById(session.getId());
        assertThat(repository.findById(session.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes WHERE attribute_name='synthetic'",Long.class)).isZero();
    }
}
