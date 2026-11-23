package com.flowpay.statements.application;

import com.flowpay.finance.api.FinanceAccountReader;
import com.flowpay.identity.api.TenantAccess;
import com.flowpay.statements.domain.StatementCsvParser;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StatementPreviewService {
    private final TenantAccess tenantAccess;
    private final FinanceAccountReader accounts;
    private final JdbcTemplate jdbc;
    private final StatementCsvParser parser = new StatementCsvParser();

    public StatementPreviewService(TenantAccess tenantAccess, FinanceAccountReader accounts, JdbcTemplate jdbc) {
        this.tenantAccess = tenantAccess;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StatementCsvParser.Preview preview(String email, UUID accountId, byte[] file) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireImporter(tenant.role());
        requireActiveAccount(tenant.id(), accountId);
        return parse(file);
    }

    @Transactional
    public ConfirmationResult confirm(String email, UUID accountId, byte[] file) {
        var tenant = tenantAccess.requireSingleTenant(email);
        requireImporter(tenant.role());
        requireActiveAccount(tenant.id(), accountId);
        var preview = parse(file);
        String checksum = checksum(file);
        var importId = UUID.randomUUID();
        var inserted = jdbc.query("""
                INSERT INTO statement_import
                  (id, tenant_id, financial_account_id, file_checksum, file_content, total_rows, accepted_rows, rejected_rows, duplicate_rows)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, financial_account_id, file_checksum) DO NOTHING
                RETURNING id
                """, (rs, row) -> rs.getObject(1, UUID.class), importId, tenant.id(), accountId, checksum, file,
                preview.rows().size(), preview.acceptedCount(), preview.rejectedCount(), preview.duplicateCount());
        if (inserted.isEmpty()) {
            return jdbc.queryForObject("""
                    SELECT id, total_rows, accepted_rows, rejected_rows, duplicate_rows
                    FROM statement_import WHERE tenant_id = ? AND financial_account_id = ? AND file_checksum = ?
                    """, (rs, row) -> new ConfirmationResult(rs.getObject("id", UUID.class), true,
                    rs.getInt("total_rows"), rs.getInt("accepted_rows"), rs.getInt("rejected_rows"), rs.getInt("duplicate_rows")),
                    tenant.id(), accountId, checksum);
        }

        jdbc.batchUpdate("""
                INSERT INTO statement_line
                  (id, tenant_id, financial_account_id, statement_import_id, row_number, effective_date, description, direction, amount_minor, row_status, validation_errors)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, preview.rows(), 500, (statement, row) -> {
            boolean rejected = !row.errors().isEmpty();
            String status = rejected ? "REJECTED" : row.duplicateInFile() ? "DUPLICATE" : "ACCEPTED";
            String errors = row.errors().stream().map(error -> error.column() + ": " + error.message()).collect(java.util.stream.Collectors.joining("; "));
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tenant.id());
            statement.setObject(3, accountId);
            statement.setObject(4, importId);
            statement.setInt(5, row.rowNumber());
            statement.setObject(6, row.date());
            statement.setString(7, row.description());
            statement.setString(8, row.direction());
            if (row.amountMinor() == null) statement.setNull(9, java.sql.Types.BIGINT);
            else statement.setLong(9, Long.parseLong(row.amountMinor()));
            statement.setString(10, status);
            statement.setString(11, errors);
        });
        return new ConfirmationResult(importId, false, preview.rows().size(), preview.acceptedCount(),
                preview.rejectedCount(), preview.duplicateCount());
    }

    @Transactional(readOnly = true)
    public ImportPage listImports(String email, UUID accountId, int page, int size) {
        validatePage(page, size);
        var tenant = tenantAccess.requireSingleTenant(email);
        long total = jdbc.queryForObject("""
                SELECT count(*) FROM statement_import WHERE tenant_id = ? AND financial_account_id = ?
                """, Long.class, tenant.id(), accountId);
        var items = jdbc.query("""
                SELECT id, financial_account_id, created_at, total_rows, accepted_rows, rejected_rows, duplicate_rows
                FROM statement_import WHERE tenant_id = ? AND financial_account_id = ?
                ORDER BY created_at DESC, id LIMIT ? OFFSET ?
                """, (rs, row) -> new ImportSummary(rs.getObject("id", UUID.class),
                rs.getObject("financial_account_id", UUID.class), rs.getTimestamp("created_at").toInstant(),
                rs.getInt("total_rows"), rs.getInt("accepted_rows"), rs.getInt("rejected_rows"), rs.getInt("duplicate_rows")),
                tenant.id(), accountId, size, page * size);
        return new ImportPage(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public LinePage listLines(String email, UUID importId, int page, int size) {
        validatePage(page, size);
        var tenant = tenantAccess.requireSingleTenant(email);
        Long total = jdbc.query("""
                SELECT count(*) FROM statement_import WHERE tenant_id = ? AND id = ?
                """, (rs, row) -> rs.getLong(1), tenant.id(), importId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Importação não encontrada."));
        var items = jdbc.query("""
                SELECT id, row_number, effective_date, description, direction, amount_minor, row_status, validation_errors, review_status, review_reason
                FROM statement_line WHERE tenant_id = ? AND statement_import_id = ?
                ORDER BY row_number, id LIMIT ? OFFSET ?
                """, (rs, row) -> {
            var date = rs.getDate("effective_date");
            return new StatementLineView(rs.getObject("id", UUID.class), rs.getInt("row_number"),
                    date == null ? null : date.toLocalDate(), rs.getString("description"), rs.getString("direction"),
                    rs.getString("amount_minor"), rs.getString("row_status"), rs.getString("validation_errors"),
                    rs.getString("review_status"), rs.getString("review_reason"));
        }, tenant.id(), importId, size, page * size);
        return new LinePage(items, total, page, size);
    }

    private void requireActiveAccount(UUID tenantId, UUID accountId) {
        if (!accounts.isActiveAccount(tenantId, accountId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta financeira não encontrada.");
    }

    private static void requireImporter(String role) {
        if (!"OWNER".equals(role) && !"MANAGER".equals(role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu papel não permite importar extratos.");
    }

    private StatementCsvParser.Preview parse(byte[] file) {
        try { return parser.parse(file); }
        catch (StatementCsvParser.CsvException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    private static String checksum(byte[] file) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 indisponível", impossible); }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página ou tamanho fora dos limites permitidos.");
    }

    public record ConfirmationResult(UUID importId, boolean replay, int totalRows, int acceptedCount,
                                     int rejectedCount, int duplicateCount) {}
    public record ImportSummary(UUID id, UUID accountId, Instant createdAt, int totalRows,
                                int acceptedCount, int rejectedCount, int duplicateCount) {}
    public record ImportPage(List<ImportSummary> items, long total, int page, int size) {}
    public record StatementLineView(UUID id, int rowNumber, LocalDate date, String description, String direction,
                                    String amountMinor, String status, String validationErrors, String reviewStatus, String reviewReason) {}
    public record LinePage(List<StatementLineView> items, long total, int page, int size) {}
}
