package com.flowpay.payment.internal;

import com.flowpay.payment.CreatedPayment;
import com.flowpay.payment.NewPayment;
import com.flowpay.payment.PaymentOperations;
import com.flowpay.payment.PaymentView;
import com.flowpay.payment.ContentionTimeoutException;
import com.flowpay.payment.DemoLimitReachedException;
import com.flowpay.payment.IdempotencyKeyReusedException;
import com.flowpay.payment.internal.domain.PaymentAmount;
import com.flowpay.company.CompanyAccess;
import com.flowpay.company.ContractValidationException;
import com.flowpay.customer.CustomerAccess;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
class PaymentCreationService implements PaymentOperations {
    private static final Pattern KEY = Pattern.compile("[!-~]{1,128}");
    private static final Pattern OUTER_WHITESPACE = Pattern.compile("\\A\\s+|\\s+\\z", Pattern.UNICODE_CHARACTER_CLASS);
    private static final BigDecimal MAXIMUM = new BigDecimal("99999999999999999.99");
    private final JdbcTemplate jdbc;
    private final CompanyAccess companies;
    private final CustomerAccess customers;
    private final Clock clock;
    private final PaymentTransitionService transitions;

    PaymentCreationService(JdbcTemplate jdbc, CompanyAccess companies, CustomerAccess customers, Clock clock,
            PaymentTransitionService transitions) {
        this.jdbc = jdbc;
        this.companies = companies;
        this.customers = customers;
        this.clock = clock;
        this.transitions = transitions;
    }

    @Override
    public PaymentView process(UUID companyId, UUID actorUserId, UUID paymentId) {
        return transitions.process(companyId, actorUserId, paymentId);
    }

    @Override
    public PaymentView cancel(UUID companyId, UUID actorUserId, UUID paymentId) {
        return transitions.cancel(companyId, actorUserId, paymentId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteExpiredDemoPayments(UUID companyId, Instant cutoff) {
        companies.lockExpiredDemo(companyId, cutoff)
                .orElseThrow(() -> new IllegalStateException("Payment cleanup requires a locked expired demo."));
        jdbc.update("DELETE FROM payment_event WHERE company_id=?", companyId);
        jdbc.update("DELETE FROM idempotency_record WHERE company_id=?", companyId);
        jdbc.update("DELETE FROM payment WHERE company_id=?", companyId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreatedPayment create(UUID companyId, UUID actorUserId, String idempotencyKey, NewPayment command) {
        var normalized = normalize(idempotencyKey, command);
        String hash = CreationRepresentation.hash(normalized);
        try {
            var company = companies.requireActive(companyId);
            String previousTimeout = jdbc.queryForObject("SELECT current_setting('lock_timeout')", String.class);
            // Both demo company locks and conventional unique-index arbitration are bounded.
            jdbc.queryForObject("SELECT set_config('lock_timeout', '3s', true)", String.class);
            if (company.demo()) companies.lockActive(companyId);
            var result = reserveOrReplay(companyId, actorUserId, idempotencyKey, normalized, hash, company.demo());
            jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, previousTimeout);
            return result;
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && ("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) {
                    // Propagate out of the transaction; PostgreSQL has aborted it. Never attempt further SQL.
                    throw new ContentionTimeoutException();
                }
            }
            throw exception;
        }
    }

    private CreatedPayment reserveOrReplay(UUID companyId, UUID actorUserId, String key, NewPayment command,
            String hash, boolean demo) {
        int reserved = jdbc.update("""
                INSERT INTO idempotency_record(id,company_id,operation,key,request_hash)
                VALUES (?,?,'create-payment',?,?) ON CONFLICT (company_id,operation,key) DO NOTHING
                """, UUID.randomUUID(), companyId, key, hash);
        if (reserved == 0) {
            // A separate READ COMMITTED statement sees the conflicting transaction after its commit.
            return jdbc.queryForObject("""
                    SELECT request_hash,payment_id,http_status,response_json FROM idempotency_record
                    WHERE company_id=? AND operation='create-payment' AND key=?
                    """, (row, index) -> {
                if (!hash.equals(row.getString("request_hash"))) throw new IdempotencyKeyReusedException();
                String json = row.getString("response_json");
                if (json == null) throw new IllegalStateException("An idempotency result was not completed.");
                return new CreatedPayment(row.getInt("http_status"), location(row.getObject("payment_id", UUID.class)), json, true);
            }, companyId, key);
        }
        // Replay/hash conflict precedes reference and quota checks, including the 500th payment retry.
        customers.requireOwned(companyId, command.customerId());
        if (demo && jdbc.queryForObject("SELECT count(*) FROM payment WHERE company_id=?", Long.class, companyId) >= 500) {
            throw new DemoLimitReachedException();
        }
        UUID payment = UUID.randomUUID();
        var now = clock.instant();
        var timestamp = Timestamp.from(now);
        jdbc.update("""
                INSERT INTO payment(id,company_id,customer_id,amount,currency,status,description,simulation_scenario,created_at,updated_at)
                VALUES (?,?,?,?,'BRL','PENDING',?,?,?,?)
                """, payment, companyId, command.customerId(), command.amount(), command.description(),
                command.simulationScenario().name(), timestamp, timestamp);
        jdbc.update("""
                INSERT INTO payment_event(id,company_id,payment_id,sequence,previous_status,new_status,reason_code,actor_user_id,created_at)
                VALUES (?,?,?,1,NULL,'PENDING',NULL,?,?)
                """, UUID.randomUUID(), companyId, payment, actorUserId, timestamp);
        String response = CreationRepresentation.response(payment, command, now);
        int completed = jdbc.update("""
                UPDATE idempotency_record SET payment_id=?,http_status=201,response_json=?
                WHERE company_id=? AND operation='create-payment' AND key=?
                """, payment, response, companyId, key);
        if (completed != 1) throw new IllegalStateException("The idempotency reservation could not be completed.");
        return new CreatedPayment(201, location(payment), response, false);
    }

    private String location(UUID payment) {
        return "/api/v1/payments/" + payment;
    }

    private NewPayment normalize(String key, NewPayment command) {
        if (key == null || key.isEmpty()) throw invalid("Idempotency-Key", "REQUIRED", "An idempotency key is required.");
        if (!KEY.matcher(key).matches()) throw invalid("Idempotency-Key", "INVALID_FORMAT", "Use 1 to 128 printable ASCII characters without spaces.");
        if (command == null) throw invalid("$", "REQUIRED", "A payment request is required.");
        if (command.customerId() == null) throw invalid("customerId", "REQUIRED", "Customer is required.");
        BigDecimal amount = command.amount();
        if (amount == null) throw invalid("amount", "REQUIRED", "Amount is required.");
        if (amount.scale() > 2) throw invalid("amount", "INVALID_FORMAT", "Amount must have at most two decimal places.");
        if (amount.signum() <= 0 || amount.compareTo(MAXIMUM) > 0) throw invalid("amount", "OUT_OF_RANGE", "Amount is outside the supported range.");
        amount = PaymentAmount.parse(amount.toPlainString()).value();
        if (command.currency() == null) throw invalid("currency", "REQUIRED", "Currency is required.");
        if (!"BRL".equals(command.currency())) throw invalid("currency", "INVALID_FORMAT", "Currency must be BRL.");
        if (command.simulationScenario() == null) throw invalid("simulationScenario", "REQUIRED", "Simulation scenario is required.");
        String description = command.description() == null ? null : OUTER_WHITESPACE.matcher(command.description()).replaceAll("");
        if (description != null && description.codePointCount(0, description.length()) > 500) {
            throw invalid("description", "TOO_LONG", "Description must contain at most 500 characters.");
        }
        if ("".equals(description)) description = null;
        return new NewPayment(command.customerId(), amount, "BRL", description, command.simulationScenario());
    }

    private ContractValidationException invalid(String field, String code, String message) {
        return new ContractValidationException(field, code, message);
    }
}
