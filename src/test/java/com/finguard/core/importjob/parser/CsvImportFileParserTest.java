package com.finguard.core.importjob.parser;

import com.finguard.core.importjob.exception.ImportFileParseException;
import com.finguard.core.importjob.exception.InvalidImportFileRequestException;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportFileRequestErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvImportFileParserTest {

    private static final String HEADER =
            "account_no,external_transaction_no,direction,amount,"
                    + "transaction_time,description";

    private static final byte[] UTF_8_BOM = {
            (byte) 0xEF,
            (byte) 0xBB,
            (byte) 0xBF
    };

    private final CsvImportFileParser parser = new CsvImportFileParser();

    @Test
    void shouldParseQuotedCommaEscapedQuoteAndMultilineDescription() {
        String csv = HEADER + "\r\n"
                + "BANK-001,EXT-001,INCOME,1200.50,"
                + "2026-07-31 09:30:15,\"Lunch, client meeting\"\r\n"
                + "BANK-001,EXT-002,EXPENSE,20.00,"
                + "2026-07-31 10:00:00,\"He said \"\"thanks\"\"\"\r\n"
                + "BANK-001,EXT-003,EXPENSE,8.00,"
                + "2026-07-31 11:00:00,\"first line\nsecond line\"\r\n";

        ParsedImportFile result = parser.parse(
                "transactions.CSV",
                utf8(csv)
        );

        assertThat(result.originalFileName()).isEqualTo("transactions.CSV");
        assertThat(result.fileSizeBytes()).isEqualTo(utf8(csv).length);
        assertThat(result.fileHash()).matches("[0-9a-f]{64}");
        assertThat(result.rows())
                .extracting(ParsedCsvRow::rowNumber)
                .containsExactly(2, 3, 4);
        assertThat(result.rows().get(0).values())
                .containsExactly(
                        "BANK-001",
                        "EXT-001",
                        "INCOME",
                        "1200.50",
                        "2026-07-31 09:30:15",
                        "Lunch, client meeting"
                );
        assertThat(result.rows().get(1).values().get(5))
                .isEqualTo("He said \"thanks\"");
        assertThat(result.rows().get(2).values().get(5))
                .isEqualTo("first line\nsecond line");
    }

    @Test
    void shouldAcceptLeadingUtf8BomAndKeepLogicalRecordNumbers() {
        String csv = HEADER + "\n"
                + "BANK-001,EXT-001,INCOME,1.00,"
                + "2026-07-31 09:30:15,\"line one\nline two\"\n"
                + "BANK-001,EXT-002,EXPENSE,2.00,"
                + "2026-07-31 09:31:15,test\n";
        byte[] bytes = concat(UTF_8_BOM, utf8(csv));

        ParsedImportFile result = parser.parse("bom.csv", bytes);

        assertThat(result.rows())
                .extracting(ParsedCsvRow::rowNumber)
                .containsExactly(2, 3);
        assertThat(result.rows().get(0).values().get(5))
                .isEqualTo("line one\nline two");
    }

    @Test
    void shouldPreserveBlankAndWrongColumnCountRecordsForDay4() {
        String csv = HEADER + "\n"
                + "\n"
                + "BANK-001,EXT-ONLY\n";

        ParsedImportFile result = parser.parse("row-errors.csv", utf8(csv));

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(result.rows().get(0).values()).containsExactly("");
        assertThat(result.rows().get(1).rowNumber()).isEqualTo(3);
        assertThat(result.rows().get(1).values())
                .containsExactly("BANK-001", "EXT-ONLY");
    }

    @Test
    void shouldProduceDeterministicHashFromOriginalBytes() {
        byte[] lf = utf8(validCsv("\n"));
        byte[] sameLf = lf.clone();
        byte[] crlf = utf8(validCsv("\r\n"));
        byte[] withBom = concat(UTF_8_BOM, lf);
        byte[] withWhitespace = utf8(
                validCsv("\n").replace(",test\n", ",test \n")
        );

        String hash = parser.parse("same.csv", lf).fileHash();

        assertThat(parser.parse("same.csv", sameLf).fileHash())
                .isEqualTo(hash);
        assertThat(parser.parse("newline.csv", crlf).fileHash())
                .isNotEqualTo(hash);
        assertThat(parser.parse("bom.csv", withBom).fileHash())
                .isNotEqualTo(hash);
        assertThat(parser.parse("space.csv", withWhitespace).fileHash())
                .isNotEqualTo(hash);
    }

    @Test
    void shouldPrepareFingerprintBeforeStructuralParsing() {
        byte[] malformedCsv = utf8("not,the,required,header\n");

        PreparedImportFile prepared = parser.prepare(
                "prepared.csv",
                malformedCsv
        );

        assertThat(prepared.originalFileName()).isEqualTo("prepared.csv");
        assertThat(prepared.fileSizeBytes()).isEqualTo(malformedCsv.length);
        assertThat(prepared.fileHash()).matches("[0-9a-f]{64}");
        assertFileFailure(
                () -> parser.parse(prepared),
                ImportFileErrorCode.INVALID_HEADER
        );
    }

    @Test
    void shouldDefensivelyCopyPreparedFileBytes() {
        byte[] bytes = utf8(validCsv("\n"));
        byte firstByte = bytes[0];

        PreparedImportFile prepared = parser.prepare("copy.csv", bytes);
        bytes[0] = (byte) 'X';
        byte[] exposed = prepared.originalBytes();
        exposed[0] = (byte) 'Y';

        assertThat(prepared.originalBytes()[0]).isEqualTo(firstByte);
        assertThat(parser.parse(prepared).rows()).hasSize(1);
    }

    @Test
    void shouldPreserveRawFieldTextWithoutBusinessNormalization() {
        String csv = HEADER + "\n"
                + " BANK-001 , EXT-001 ,income,01.0,"
                + " 2026-07-31 09:30:15 , test \n";

        ParsedCsvRow row = parser.parse("raw.csv", utf8(csv))
                .rows()
                .get(0);

        assertThat(row.values()).containsExactly(
                " BANK-001 ",
                " EXT-001 ",
                "income",
                "01.0",
                " 2026-07-31 09:30:15 ",
                " test "
        );
    }

    @Test
    void shouldAcceptFileAtExactFiveMebibyteBoundary() {
        String prefix = HEADER + "\n"
                + "BANK-001,EXT-001,INCOME,1.00,"
                + "2026-07-31 09:30:15,";
        int descriptionLength =
                CsvImportFileParser.MAX_FILE_SIZE_BYTES
                        - utf8(prefix).length;
        byte[] bytes = utf8(prefix + "a".repeat(descriptionLength));

        ParsedImportFile result = parser.parse("maximum-size.csv", bytes);

        assertThat(bytes)
                .hasSize(CsvImportFileParser.MAX_FILE_SIZE_BYTES);
        assertThat(result.fileSizeBytes())
                .isEqualTo(CsvImportFileParser.MAX_FILE_SIZE_BYTES);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).values().get(5))
                .hasSize(descriptionLength);
    }

    @Test
    void shouldRejectInvalidRequestBeforeParsing() {
        assertRequestFailure(
                () -> parser.parse(null, utf8(validCsv("\n"))),
                ImportFileRequestErrorCode.MISSING_FILE_NAME
        );
        assertRequestFailure(
                () -> parser.parse(" ", utf8(validCsv("\n"))),
                ImportFileRequestErrorCode.MISSING_FILE_NAME
        );
        assertRequestFailure(
                () -> parser.parse("transactions.txt", utf8(validCsv("\n"))),
                ImportFileRequestErrorCode.INVALID_FILE_EXTENSION
        );
        assertRequestFailure(
                () -> parser.parse("transactions.csv", null),
                ImportFileRequestErrorCode.EMPTY_FILE
        );
        assertRequestFailure(
                () -> parser.parse("transactions.csv", new byte[0]),
                ImportFileRequestErrorCode.EMPTY_FILE
        );
        assertRequestFailure(
                () -> parser.parse(
                        "transactions.csv",
                        new byte[CsvImportFileParser.MAX_FILE_SIZE_BYTES + 1]
                ),
                ImportFileRequestErrorCode.FILE_TOO_LARGE
        );
    }

    @Test
    void shouldRejectMalformedUtf8AndInvalidBomPositions() {
        byte[] invalidUtf8 = concat(
                utf8(HEADER + "\n"),
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );
        byte[] misplacedBom = concat(
                utf8(HEADER + "\n"),
                UTF_8_BOM,
                utf8("BANK-001,EXT-001,INCOME,1.00,"
                        + "2026-07-31 09:30:15,test\n")
        );
        byte[] duplicateBom = concat(
                UTF_8_BOM,
                UTF_8_BOM,
                utf8(validCsv("\n"))
        );

        assertFileFailure(
                () -> parser.parse("invalid.csv", invalidUtf8),
                ImportFileErrorCode.INVALID_UTF8
        );
        assertFileFailure(
                () -> parser.parse("misplaced.csv", misplacedBom),
                ImportFileErrorCode.INVALID_UTF8
        );
        assertFileFailure(
                () -> parser.parse("duplicate.csv", duplicateBom),
                ImportFileErrorCode.INVALID_UTF8
        );
    }

    @Test
    void shouldRejectBareCarriageReturnAsMalformedCsv() {
        String bareRecordSeparator = HEADER + "\r"
                + "BANK-001,EXT-001,INCOME,1.00,"
                + "2026-07-31 09:30:15,test";
        String bareCarriageReturnInsideQuote = HEADER + "\n"
                + "BANK-001,EXT-001,INCOME,1.00,"
                + "2026-07-31 09:30:15,\"first\rsecond\"";

        assertFileFailure(
                () -> parser.parse(
                        "bare-record.csv",
                        utf8(bareRecordSeparator)
                ),
                ImportFileErrorCode.MALFORMED_CSV
        );
        assertFileFailure(
                () -> parser.parse(
                        "bare-quoted.csv",
                        utf8(bareCarriageReturnInsideQuote)
                ),
                ImportFileErrorCode.MALFORMED_CSV
        );
    }

    @Test
    void shouldRejectEveryHeaderMismatchAsInvalidHeader() {
        List<String> invalidHeaders = List.of(
                "",
                "account_no,external_transaction_no,direction,amount,"
                        + "transaction_time",
                HEADER + ",extra",
                "external_transaction_no,account_no,direction,amount,"
                        + "transaction_time,description",
                "ACCOUNT_NO,external_transaction_no,direction,amount,"
                        + "transaction_time,description",
                "account_no,account_no,direction,amount,"
                        + "transaction_time,description"
        );

        for (String invalidHeader : invalidHeaders) {
            String csv = invalidHeader + "\n"
                    + "BANK-001,EXT-001,INCOME,1.00,"
                    + "2026-07-31 09:30:15,test\n";
            assertFileFailure(
                    () -> parser.parse("header.csv", utf8(csv)),
                    ImportFileErrorCode.INVALID_HEADER
            );
        }
    }

    @Test
    void shouldRejectUnclosedQuoteWithoutLeakingParserDetails() {
        String csv = HEADER + "\n"
                + "BANK-001,EXT-001,INCOME,1.00,"
                + "2026-07-31 09:30:15,\"unclosed";

        assertThatThrownBy(() -> parser.parse("malformed.csv", utf8(csv)))
                .isInstanceOfSatisfying(
                        ImportFileParseException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ImportFileErrorCode.MALFORMED_CSV
                                    );
                            assertThat(exception.getMessage())
                                    .isEqualTo("CSV structure is malformed")
                                    .doesNotContain(
                                            "org.apache",
                                            "CSVParser",
                                            "Exception"
                                    );
                            assertThat(exception.getCause()).isNull();
                        }
                );
    }

    @Test
    void shouldRejectFileWithoutDataRows() {
        assertFileFailure(
                () -> parser.parse("header-only.csv", utf8(HEADER)),
                ImportFileErrorCode.NO_DATA_ROWS
        );
        assertFileFailure(
                () -> parser.parse("header-line.csv", utf8(HEADER + "\n")),
                ImportFileErrorCode.NO_DATA_ROWS
        );
    }

    @Test
    void shouldAcceptTenThousandRowsAndRejectTheNextRecord() {
        ParsedImportFile maximum = parser.parse(
                "maximum.csv",
                utf8(csvWithRows(CsvImportFileParser.MAX_DATA_ROWS))
        );

        assertThat(maximum.rows())
                .hasSize(CsvImportFileParser.MAX_DATA_ROWS);
        assertThat(maximum.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(maximum.rows().get(
                CsvImportFileParser.MAX_DATA_ROWS - 1
        ).rowNumber()).isEqualTo(10_001);

        assertFileFailure(
                () -> parser.parse(
                        "too-many.csv",
                        utf8(csvWithRows(
                                CsvImportFileParser.MAX_DATA_ROWS + 1
                        ))
                ),
                ImportFileErrorCode.TOO_MANY_ROWS
        );
    }

    @Test
    void shouldReturnImmutableResults() {
        ParsedImportFile result = parser.parse(
                "immutable.csv",
                utf8(validCsv("\n"))
        );

        assertThatThrownBy(() -> result.rows().add(
                new ParsedCsvRow(3, List.of("value"))
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.rows().get(0).values().add("value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private String validCsv(String lineSeparator) {
        return HEADER + lineSeparator
                + "BANK-001,EXT-001,INCOME,1.00,"
                + "2026-07-31 09:30:15,test"
                + lineSeparator;
    }

    private String csvWithRows(int rowCount) {
        StringBuilder csv = new StringBuilder(
                HEADER.length() + rowCount * 68
        );
        csv.append(HEADER).append('\n');
        for (int index = 1; index <= rowCount; index++) {
            csv.append("BANK-001,EXT-")
                    .append(index)
                    .append(",INCOME,1.00,2026-07-31 09:30:15,test\n");
        }
        return csv.toString();
    }

    private byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    private void assertRequestFailure(
            ThrowingOperation operation,
            ImportFileRequestErrorCode expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        InvalidImportFileRequestException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(expectedCode);
                            assertThat(exception.getMessage()).isNotBlank();
                            assertThat(exception.getCause()).isNull();
                        }
                );
    }

    private void assertFileFailure(
            ThrowingOperation operation,
            ImportFileErrorCode expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        ImportFileParseException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(expectedCode);
                            assertThat(exception.getMessage()).isNotBlank();
                            assertThat(exception.getCause()).isNull();
                        }
                );
    }

    @FunctionalInterface
    private interface ThrowingOperation {

        void run();
    }
}
