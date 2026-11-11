package com.flowpay.payment.internal;

import com.flowpay.company.CompanyAccess;
import com.flowpay.payment.ContentionTimeoutException;
import com.flowpay.payment.PaymentFinalStateException;
import com.flowpay.payment.PaymentNotFoundException;
import com.flowpay.payment.PaymentStatus;
import com.flowpay.payment.PaymentView;
import com.flowpay.payment.SimulationScenario;
import com.flowpay.payment.internal.domain.PaymentRules;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentTransitionService {
    private record LockedPayment(PaymentStatus status, SimulationScenario scenario) {}

    private final JdbcTemplate jdbc;
    private final CompanyAccess companies;
    private final Clock clock;

    PaymentTransitionService(JdbcTemplate jdbc, CompanyAccess companies, Clock clock) {
        this.jdbc = jdbc;
        this.companies = companies;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    PaymentView process(UUID companyId, UUID actorUserId, UUID paymentId) {
        return transition(companyId, actorUserId, paymentId, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    PaymentView cancel(UUID companyId, UUID actorUserId, UUID paymentId) {
        return transition(companyId, actorUserId, paymentId, true);
    }

    private PaymentView transition(UUID companyId, UUID actorUserId, UUID paymentId, boolean cancel) {
        try {
            var company = companies.requireActive(companyId);
            if (company.demo()) companies.lockActive(companyId);
            String previousTimeout = jdbc.queryForObject("SELECT current_setting('lock_timeout')", String.class);
            jdbc.queryForObject("SELECT set_config('lock_timeout', '3s', true)", String.class);

            List<LockedPayment> matches = jdbc.query("""
                    SELECT status,simulation_scenario FROM payment WHERE id=? AND company_id=? FOR UPDATE
                    """, (row, index) -> new LockedPayment(PaymentStatus.valueOf(row.getString("status")),
                    SimulationScenario.valueOf(row.getString("simulation_scenario"))), paymentId, companyId);
            if (matches.isEmpty()) throw new PaymentNotFoundException();

            LockedPayment current = matches.getFirst();
            PaymentRules.ProcessingResult result;
            try {
                result = cancel
                        ? new PaymentRules.ProcessingResult(PaymentRules.transition(current.status(), PaymentStatus.CANCELLED), null)
                        : PaymentRules.process(current.status(), current.scenario());
            } catch (com.flowpay.payment.internal.domain.PaymentFinalStateException exception) {
                throw new PaymentFinalStateException(exception.status());
            }

            Timestamp now = Timestamp.from(clock.instant());
            int updated = jdbc.update("""
                    UPDATE payment SET status=?,updated_at=? WHERE id=? AND company_id=? AND status='PENDING'
                    """, result.status().name(), now, paymentId, companyId);
            if (updated != 1) throw new IllegalStateException("The locked payment changed unexpectedly.");
            long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence),0)+1 FROM payment_event WHERE payment_id=?", Long.class, paymentId);
            jdbc.update("""
                    INSERT INTO payment_event(id,company_id,payment_id,sequence,previous_status,new_status,reason_code,actor_user_id,created_at)
                    VALUES (?,?,?,?,?,?,?, ?,?)
                    """, UUID.randomUUID(), companyId, paymentId, sequence, current.status().name(),
                    result.status().name(), result.reasonCode(), actorUserId, now);
            jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, previousTimeout);
            return readUpdated(companyId, paymentId);
        } catch (PaymentFinalStateException | PaymentNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (isContention(exception)) throw new ContentionTimeoutException();
            throw exception;
        }
    }

    private PaymentView readUpdated(UUID companyId, UUID paymentId) {
        return jdbc.queryForObject("""
                SELECT id,customer_id,amount,currency,status,description,simulation_scenario,created_at,updated_at
                FROM payment WHERE id=? AND company_id=?
                """, (row, index) -> new PaymentView(row.getObject("id", UUID.class),
                row.getObject("customer_id", UUID.class), row.getBigDecimal("amount"), row.getString("currency"),
                PaymentStatus.valueOf(row.getString("status")), row.getString("description"),
                SimulationScenario.valueOf(row.getString("simulation_scenario")),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant()), paymentId, companyId);
    }

    private boolean isContention(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && ("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) return true;
        }
        return exception instanceof DataAccessException && exception.getMessage() != null
                && exception.getMessage().contains("lock timeout");
    }
}
