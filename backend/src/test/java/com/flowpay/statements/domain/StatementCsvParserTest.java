package com.flowpay.statements.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StatementCsvParserTest {
    private final StatementCsvParser parser = new StatementCsvParser();

    @Test
    void previewsBOMQuotedDescriptionsAndExplicitDirectionsWithoutDiscardingDuplicateEvidence() {
        var csv = "\uFEFFdata;descricao;valor;direcao\r\n01/03/2026;\"Serviço; mensal\";1.234,56;ENTRADA\r\n"
                + "01/03/2026;\"Serviço; mensal\";1.234,56;ENTRADA\r\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).amountMinor()).isEqualTo("123456");
        assertThat(result.rows().get(0).description()).isEqualTo("Serviço; mensal");
        assertThat(result.rows().get(1).duplicateInFile()).isTrue();
    }

    @Test
    void reportsInvalidValuesByColumnAndDoesNotGuessDirection() {
        var result = parser.parse(("data;descricao;valor;direcao\n31/02/2026;Serviço;10,001;credito\n")
                .getBytes(StandardCharsets.UTF_8));

        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().errors()).extracting(StatementCsvParser.FieldError::column)
                .contains("data", "valor", "direcao");
    }

    @Test
    void rejectsMalformedCsvAndHeaders() {
        assertThatThrownBy(() -> parser.parse("wrong;header\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StatementCsvParser.CsvException.class).hasMessageContaining("Cabeçalho");
        assertThatThrownBy(() -> parser.parse("data;descricao;valor;direcao\n01/01/2026;\"aberto;1,00;ENTRADA\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StatementCsvParser.CsvException.class).hasMessageContaining("não foi fechado");
    }

    @Test
    void rejectsInvalidUtf8AndOversizedInput() {
        assertThatThrownBy(() -> parser.parse(new byte[] {(byte) 0xC3, 0x28}))
                .isInstanceOf(StatementCsvParser.CsvException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> parser.parse(new byte[StatementCsvParser.MAX_FILE_BYTES + 1]))
                .isInstanceOf(StatementCsvParser.CsvException.class).hasMessageContaining("10 MB");
    }

    @Test
    void stopsParsingAsSoonAsTheDataRowLimitIsExceeded() {
        var csv = new StringBuilder("data;descricao;valor;direcao\n");
        for (int i = 0; i < StatementCsvParser.MAX_DATA_ROWS; i++) {
            csv.append("01/01/2026;Servico;1,00;ENTRADA\n");
        }
        csv.append("excesso;linha;1,00;ENTRADA\n\"CSV malformado depois do limite");

        assertThatThrownBy(() -> parser.parse(csv.toString().getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(StatementCsvParser.CsvException.class).hasMessageContaining("50.000 linhas");
    }
}
