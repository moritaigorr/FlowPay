package com.flowpay.payment.internal;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.ContractValidationException;
import com.flowpay.payment.PaymentSummary;
import com.flowpay.payment.PaymentSummaryReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentSummaryService implements PaymentSummaryReader {
    private final CompanyAccess companies;
    private final JdbcTemplate jdbc;

    PaymentSummaryService(CompanyAccess companies, JdbcTemplate jdbc) {
        this.companies = companies;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummary summarize(UUID companyId, Instant from, Instant to) {
        if (from == null || to == null) throw new ContractValidationException("from", "INVALID_FORMAT", "Both period bounds are required.");
        if (!from.isBefore(to)) throw new ContractValidationException("from", "OUT_OF_RANGE", "From must be before to.");
        companies.requireActive(companyId);
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE status='APPROVED'), 0) AS approved_volume,
                       COUNT(*) FILTER (WHERE status='PENDING') AS pending_count,
                       COALESCE(SUM(amount) FILTER (WHERE status='PENDING'), 0) AS pending_amount,
                       COUNT(*) FILTER (WHERE status='APPROVED') AS approved_count,
                       COUNT(*) FILTER (WHERE status='REJECTED') AS rejected_count,
                       COUNT(*) AS total_payments
                FROM payment
                WHERE company_id=? AND created_at>=? AND created_at<?
                """, (rows, rowNumber) -> summary(rows, from, to), companyId, bound(from), bound(to));
    }

    private PaymentSummary summary(ResultSet row, Instant from, Instant to) throws SQLException {
        BigDecimal approvedVolume = row.getBigDecimal("approved_volume");
        BigDecimal pendingAmount = row.getBigDecimal("pending_amount");
        long approvedCount = row.getLong("approved_count");
        long rejectedCount = row.getLong("rejected_count");
        long denominator = approvedCount + rejectedCount;
        String approvalRate = denominator == 0 ? null : BigDecimal.valueOf(approvedCount)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP).toPlainString();
        return new PaymentSummary(from, to, "BRL", approvedVolume.setScale(2), row.getLong("pending_count"),
                pendingAmount.setScale(2), approvedCount, rejectedCount, row.getLong("total_payments"), approvalRate);
    }

    private Timestamp bound(Instant value) {
        // PostgreSQL stores microseconds. Ceil bounds to preserve exact membership for sub-microsecond instants.
        Instant micros = value.truncatedTo(ChronoUnit.MICROS);
        return Timestamp.from(micros.equals(value) ? micros : micros.plus(1, ChronoUnit.MICROS));
    }
}
