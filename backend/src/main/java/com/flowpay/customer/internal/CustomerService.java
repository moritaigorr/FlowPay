package com.flowpay.customer.internal;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.ContractValidationException;
import com.flowpay.customer.ContentionTimeoutException;
import com.flowpay.customer.CustomerAccess;
import com.flowpay.customer.CustomerListing;
import com.flowpay.customer.CustomerNotFoundException;
import com.flowpay.customer.CustomerOperations;
import com.flowpay.customer.CustomerPage;
import com.flowpay.customer.CustomerView;
import com.flowpay.customer.DemoLimitReachedException;
import com.flowpay.customer.NewCustomer;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
class CustomerService implements CustomerAccess, CustomerOperations, CustomerListing {
    private static final Pattern OUTER_WHITESPACE = Pattern.compile("\\A\\s+|\\s+\\z", Pattern.UNICODE_CHARACTER_CLASS);
    private final JdbcTemplate jdbc;
    private final CompanyAccess companies;
    private final Validator validator;
    private final Clock clock;

    CustomerService(JdbcTemplate jdbc, CompanyAccess companies, Validator validator, Clock clock) {
        this.jdbc = jdbc;
        this.companies = companies;
        this.validator = validator;
        this.clock = clock;
    }

    private record EmailValue(@Email String value) {}

    private String stripped(String value) {
        return value == null ? "" : OUTER_WHITESPACE.matcher(value).replaceAll("");
    }

    private String required(String value, String field, int limit) {
        String normalized = stripped(value);
        if (normalized.isEmpty()) throw new ContractValidationException(field, "REQUIRED", "This field is required.");
        if (normalized.codePointCount(0, normalized.length()) > limit) {
            throw new ContractValidationException(field, "TOO_LONG", "This field exceeds its character limit.");
        }
        return normalized;
    }

    @Override
    @Transactional
    public CustomerView create(UUID companyId, NewCustomer command) {
        String name = required(command.name(), "name", 120);
        String email = required(command.email() == null ? null : stripped(command.email()).toLowerCase(Locale.ROOT), "email", 254);
        if (!validator.validate(new EmailValue(email)).isEmpty()) {
            throw new ContractValidationException("email", "INVALID_FORMAT", "Email must be valid.");
        }
        try {
            var company = companies.requireActive(companyId);
            if (company.demo()) {
                // Bound only this lock acquisition; retain the caller's transaction configuration afterward.
                String previousTimeout = jdbc.queryForObject("SELECT current_setting('lock_timeout')", String.class);
                jdbc.queryForObject("SELECT set_config('lock_timeout', '3s', true)", String.class);
                companies.lockActive(companyId);
                jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, previousTimeout);
                long count = jdbc.queryForObject("SELECT count(*) FROM customer WHERE company_id = ?", Long.class, companyId);
                if (count >= 100) throw new DemoLimitReachedException();
            }
            var now = clock.instant();
            var customer = new CustomerView(UUID.randomUUID(), name, email, now, now);
            jdbc.update("""
                    INSERT INTO customer(id, company_id, name, email, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, customer.id(), companyId, name, email, Timestamp.from(now), Timestamp.from(now));
            return customer;
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && ("55P03".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState()))) {
                    throw new ContentionTimeoutException();
                }
            }
            throw exception;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteExpiredDemoCustomers(UUID companyId, Instant cutoff) {
        companies.lockExpiredDemo(companyId, cutoff)
                .orElseThrow(() -> new IllegalStateException("Customer cleanup requires a locked expired demo."));
        jdbc.update("DELETE FROM customer WHERE company_id=?", companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerView requireOwned(UUID companyId, UUID customerId) {
        companies.requireActive(companyId);
        var customers = jdbc.query("SELECT id, name, email, created_at, updated_at FROM customer WHERE company_id=? AND id=?",
                (row, index) -> view(row), companyId, customerId);
        if (customers.isEmpty()) throw new CustomerNotFoundException();
        return customers.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPage list(UUID companyId, int page, int size, String query) {
        if (page < 0) throw new ContractValidationException("page", "OUT_OF_RANGE", "Page must be nonnegative.");
        if (size < 1 || size > 100) throw new ContractValidationException("size", "OUT_OF_RANGE", "Size must be between 1 and 100.");
        if (query != null && query.codePointCount(0, query.length()) > 200) {
            throw new ContractValidationException("q", "TOO_LONG", "Search must contain at most 200 characters.");
        }
        if (query != null && query.indexOf('\0') >= 0) {
            throw new ContractValidationException("q", "INVALID_FORMAT", "Search must not contain a null character.");
        }
        String pattern = "%" + stripped(query).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        companies.requireActive(companyId);
        // One statement keeps counts and items on the same READ COMMITTED snapshot, even for empty/high pages.
        return jdbc.query("""
                WITH filtered AS MATERIALIZED (
                    SELECT id, name, email, created_at, updated_at FROM customer
                    WHERE company_id = ? AND (lower(name) LIKE lower(?) ESCAPE '!' OR lower(email) LIKE lower(?) ESCAPE '!')
                )
                SELECT total.total_elements, page.* FROM (SELECT count(*) AS total_elements FROM filtered) total
                LEFT JOIN LATERAL (
                    SELECT * FROM filtered ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                ) page ON true ORDER BY page.created_at DESC, page.id DESC
                """, rows -> {
                    var items = new ArrayList<CustomerView>();
                    long total = 0;
                    while (rows.next()) {
                        total = rows.getLong("total_elements");
                        if (rows.getObject("id") != null) items.add(view(rows));
                    }
                    return new CustomerPage(items, page, size, total, total / size + (total % size == 0 ? 0 : 1));
                }, companyId, pattern, pattern, size, (long) page * size);
    }

    private CustomerView view(ResultSet row) throws SQLException {
        return new CustomerView(row.getObject("id", UUID.class), row.getString("name"), row.getString("email"),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
