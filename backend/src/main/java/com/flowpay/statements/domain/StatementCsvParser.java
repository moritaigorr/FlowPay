package com.flowpay.statements.domain;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strict parser for the documented FlowPay statement CSV dialect. */
public final class StatementCsvParser {
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 50_000;
    private static final long MAX_AMOUNT_MINOR = 9_000_000_000_000_000L;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);

    public Preview parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new CsvException("O arquivo está vazio.");
        if (bytes.length > MAX_FILE_BYTES) throw new CsvException("O arquivo excede o limite de 10 MB.");
        final String text;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString().replaceFirst("^\\uFEFF", "");
        } catch (CharacterCodingException invalid) {
            throw new CsvException("O arquivo precisa estar codificado em UTF-8.");
        }
        List<List<String>> records = records(text);
        if (records.isEmpty()) throw new CsvException("O CSV não contém cabeçalho.");
        if (!records.getFirst().equals(List.of("data", "descricao", "valor", "direcao"))) {
            throw new CsvException("Cabeçalho inválido. Use: data;descricao;valor;direcao.");
        }
        if (records.size() == 1) throw new CsvException("O CSV não contém linhas de dados.");
        if (records.size() - 1 > MAX_DATA_ROWS) throw new CsvException("O arquivo excede o limite de 50.000 linhas.");

        var rows = new ArrayList<RowPreview>();
        var seen = new java.util.HashSet<String>();
        int accepted = 0;
        int rejected = 0;
        int duplicates = 0;
        for (int i = 1; i < records.size(); i++) {
            var fields = records.get(i);
            if (fields.size() == 1 && fields.getFirst().isBlank()) continue;
            int line = i + 1;
            var errors = new ArrayList<FieldError>();
            if (fields.size() != 4) {
                errors.add(new FieldError("linha", "Cada registro deve ter quatro colunas separadas por ponto e vírgula."));
            }
            String date = field(fields, 0, "data", errors);
            String description = field(fields, 1, "descricao", errors);
            String amount = field(fields, 2, "valor", errors);
            String directionText = field(fields, 3, "direcao", errors);
            LocalDate parsedDate = null;
            Long amountMinor = null;
            String direction = null;
            try { parsedDate = LocalDate.parse(date, DATE_FORMAT); }
            catch (DateTimeParseException invalid) { errors.add(new FieldError("data", "Use uma data válida no formato dd/MM/aaaa.")); }
            if (description.isBlank() || description.length() > 250) errors.add(new FieldError("descricao", "Informe uma descrição de 1 a 250 caracteres."));
            try { amountMinor = parseAmount(amount); }
            catch (IllegalArgumentException invalid) { errors.add(new FieldError("valor", invalid.getMessage())); }
            direction = switch (directionText.strip().toUpperCase(Locale.ROOT)) {
                case "ENTRADA" -> "INFLOW";
                case "SAIDA" -> "OUTFLOW";
                default -> { errors.add(new FieldError("direcao", "Use ENTRADA ou SAIDA explicitamente.")); yield null; }
            };
            boolean duplicate = false;
            if (errors.isEmpty()) {
                String fingerprint = parsedDate + "\u0000" + direction + "\u0000" + amountMinor + "\u0000" + description.strip().toLowerCase(Locale.ROOT);
                duplicate = !seen.add(fingerprint);
                if (duplicate) duplicates++; else accepted++;
            } else rejected++;
            rows.add(new RowPreview(line, parsedDate, description.strip(), amountMinor == null ? null : Long.toString(amountMinor), direction, duplicate, List.copyOf(errors)));
        }
        return new Preview(accepted, rejected, duplicates, List.copyOf(rows));
    }

    private static long parseAmount(String value) {
        String input = value.strip();
        if (!input.matches("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]{1,2})?"))
            throw new IllegalArgumentException("Use valor positivo no formato 1.234,56, sem sinal ou moeda.");
        try {
            long minor = new BigDecimal(input.replace(".", "").replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
            if (minor <= 0 || minor > MAX_AMOUNT_MINOR) throw new ArithmeticException();
            return minor;
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("O valor precisa ser positivo e não pode exceder 90 trilhões de reais.");
        }
    }

    private static String field(List<String> fields, int index, String name, List<FieldError> errors) {
        if (index >= fields.size()) { errors.add(new FieldError(name, "Coluna ausente.")); return ""; }
        return fields.get(index).strip();
    }

    private static List<List<String>> records(String text) {
        var result = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') { quoted = false; afterQuote = true; }
                else field.append(c);
            } else if (afterQuote && c != ';' && c != '\r' && c != '\n') {
                if (!Character.isWhitespace(c)) throw new CsvException("CSV inválido: caractere inesperado após aspas.");
            } else if (c == '"') {
                if (field.length() != 0) throw new CsvException("CSV inválido: aspas fora do início de uma coluna.");
                quoted = true; afterQuote = false;
            } else if (c == ';') { row.add(field.toString()); field.setLength(0); afterQuote = false; }
            else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0); afterQuote = false;
                if (!(row.size() == 1 && row.getFirst().isBlank())) addRecord(result, row);
                row.clear();
            } else { field.append(c); afterQuote = false; }
        }
        if (quoted) throw new CsvException("CSV inválido: campo entre aspas não foi fechado.");
        if (field.length() > 0 || !row.isEmpty() || afterQuote) { row.add(field.toString()); addRecord(result, row); }
        return result;
    }

    private static void addRecord(List<List<String>> records, List<String> row) {
        if (records.size() >= MAX_DATA_ROWS + 1) {
            throw new CsvException("O arquivo excede o limite de 50.000 linhas.");
        }
        records.add(List.copyOf(row));
    }

    public record Preview(int acceptedCount, int rejectedCount, int duplicateCount, List<RowPreview> rows) {}
    public record RowPreview(int rowNumber, LocalDate date, String description, String amountMinor, String direction,
                             boolean duplicateInFile, List<FieldError> errors) {}
    public record FieldError(String column, String message) {}

    public static final class CsvException extends RuntimeException {
        public CsvException(String message) { super(message); }
    }
}
