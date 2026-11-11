package com.flowpay.payment.internal;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.ContractValidationException;
import com.flowpay.payment.PaymentAccess;
import com.flowpay.payment.PaymentEventView;
import com.flowpay.payment.PaymentFilter;
import com.flowpay.payment.PaymentListing;
import com.flowpay.payment.PaymentNotFoundException;
import com.flowpay.payment.PaymentPage;
import com.flowpay.payment.PaymentStatus;
import com.flowpay.payment.PaymentView;
import com.flowpay.payment.SimulationScenario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentQueryService implements PaymentAccess, PaymentListing {
    private static final String COLUMNS = "id, customer_id, amount, currency, status, description, simulation_scenario, created_at, updated_at";
    private static final Pattern OUTER_WHITESPACE = Pattern.compile("\\A\\s+|\\s+\\z", Pattern.UNICODE_CHARACTER_CLASS);
    private final JdbcTemplate jdbc;
    private final CompanyAccess companies;

    PaymentQueryService(JdbcTemplate jdbc, CompanyAccess companies) {
        this.jdbc = jdbc;
        this.companies = companies;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentView requireOwned(UUID companyId, UUID paymentId) {
        companies.requireActive(companyId);
        var items = jdbc.query("SELECT " + COLUMNS + " FROM payment WHERE company_id=? AND id=?",
                (row, index) -> view(row), companyId, paymentId);
        if (items.isEmpty()) throw new PaymentNotFoundException();
        return items.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentEventView> events(UUID companyId, UUID paymentId) {
        // An empty event query cannot establish that the payment is owned.
        requireOwned(companyId, paymentId);
        return List.copyOf(jdbc.query("""
                SELECT id, payment_id, sequence, previous_status, new_status, reason_code, actor_user_id, created_at
                FROM payment_event WHERE company_id=? AND payment_id=? ORDER BY sequence ASC
                """, (row, index) -> new PaymentEventView(row.getObject("id", UUID.class),
                row.getObject("payment_id", UUID.class), row.getLong("sequence"),
                row.getString("previous_status") == null ? null : PaymentStatus.valueOf(row.getString("previous_status")),
                PaymentStatus.valueOf(row.getString("new_status")), row.getString("reason_code"),
                row.getObject("actor_user_id", UUID.class), row.getTimestamp("created_at").toInstant()), companyId, paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentPage list(UUID companyId, int page, int size, PaymentFilter filter) {
        if (page < 0) throw new ContractValidationException("page", "OUT_OF_RANGE", "Page must be nonnegative.");
        if (size < 1 || size > 100) throw new ContractValidationException("size", "OUT_OF_RANGE", "Size must be between 1 and 100.");
        if (filter.query() != null && filter.query().codePointCount(0, filter.query().length()) > 200) {
            throw new ContractValidationException("q", "TOO_LONG", "Search must contain at most 200 characters.");
        }
        if (filter.query() != null && filter.query().indexOf('\0') >= 0) {
            throw new ContractValidationException("q", "INVALID_FORMAT", "Search must not contain a null character.");
        }
        if (filter.createdFrom() != null && filter.createdTo() != null && !filter.createdFrom().isBefore(filter.createdTo())) {
            throw new ContractValidationException("createdTo", "OUT_OF_RANGE", "Created to must be after created from.");
        }
        companies.requireActive(companyId);
        String query = filter.query() == null ? "" : OUTER_WHITESPACE.matcher(filter.query()).replaceAll("");
        var parameters = new ArrayList<Object>();
        var sql = new StringBuilder("WITH filtered AS MATERIALIZED (SELECT p.id, p.customer_id, p.amount, p.currency, p.status, p.description, p.simulation_scenario, p.created_at, p.updated_at FROM payment p");
        if (!query.isEmpty()) sql.append(" JOIN customer c ON c.id=p.customer_id AND c.company_id=p.company_id");
        sql.append(" WHERE p.company_id=?");
        parameters.add(companyId);
        if (filter.status() != null) {
            sql.append(" AND p.status=?");
            parameters.add(filter.status().name());
        }
        if (filter.customerId() != null) {
            sql.append(" AND p.customer_id=?");
            parameters.add(filter.customerId());
        }
        if (filter.createdFrom() != null) {
            sql.append(" AND p.created_at>=?");
            parameters.add(bound(filter.createdFrom()));
        }
        if (filter.createdTo() != null) {
            sql.append(" AND p.created_at<?");
            parameters.add(bound(filter.createdTo()));
        }
        if (!query.isEmpty()) {
            String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            sql.append(" AND (");
            UUID id = exactUuid(query);
            if (id != null) {
                sql.append("p.id=? OR ");
                parameters.add(id);
            }
            sql.append("lower(c.name) LIKE lower(?) ESCAPE '!' OR lower(c.email) LIKE lower(?) ESCAPE '!')");
            parameters.add(pattern);
            parameters.add(pattern);
        }
        // One statement gives items and total one PostgreSQL snapshot, including an empty/high page.
        sql.append("""
                ) SELECT total.total_elements, page.* FROM (SELECT count(*) AS total_elements FROM filtered) total
                LEFT JOIN LATERAL (
                    SELECT * FROM filtered ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                ) page ON true ORDER BY page.created_at DESC, page.id DESC
                """);
        parameters.add(size);
        parameters.add((long) page * size);
        return jdbc.query(sql.toString(), rows -> {
            var items = new ArrayList<PaymentView>();
            long total = 0;
            while (rows.next()) {
                total = rows.getLong("total_elements");
                if (rows.getObject("id") != null) items.add(view(rows));
            }
            return new PaymentPage(items, page, size, total, total / size + (total % size == 0 ? 0 : 1));
        }, parameters.toArray());
    }

    private UUID exactUuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Timestamp bound(Instant value) {
        // Stored instants are discrete microseconds. Ceil both bounds so JDBC rounding cannot shift [from,to).
        Instant micros = value.truncatedTo(ChronoUnit.MICROS);
        return Timestamp.from(micros.equals(value) ? micros : micros.plus(1, ChronoUnit.MICROS));
    }

    private PaymentView view(ResultSet row) throws SQLException {
        return new PaymentView(row.getObject("id", UUID.class), row.getObject("customer_id", UUID.class),
                row.getBigDecimal("amount"), row.getString("currency"), PaymentStatus.valueOf(row.getString("status")),
                row.getString("description"), SimulationScenario.valueOf(row.getString("simulation_scenario")),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
