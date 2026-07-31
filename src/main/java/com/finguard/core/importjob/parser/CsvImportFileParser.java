package com.finguard.core.importjob.parser;

import com.finguard.core.importjob.exception.ImportFileParseException;
import com.finguard.core.importjob.exception.InvalidImportFileRequestException;
import com.finguard.core.importjob.model.ImportFileErrorCode;
import com.finguard.core.importjob.model.ImportFileRequestErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Component
public class CsvImportFileParser {

    public static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 10_000;

    private static final byte[] UTF_8_BOM = {
            (byte) 0xEF,
            (byte) 0xBB,
            (byte) 0xBF
    };

    private static final List<String> REQUIRED_HEADER = List.of(
            "account_no",
            "external_transaction_no",
            "direction",
            "amount",
            "transaction_time",
            "description"
    );

    private static final CSVFormat CSV_FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(false)
            .setIgnoreHeaderCase(false)
            .setIgnoreSurroundingSpaces(false)
            .setTrim(false)
            .setLenientEof(false)
            .setTrailingData(false)
            .get();

    public ParsedImportFile parse(
            String originalFileName,
            byte[] originalBytes) {
        return parse(prepare(originalFileName, originalBytes));
    }

    public PreparedImportFile prepare(
            String originalFileName,
            byte[] originalBytes) {
        validateRequest(originalFileName, originalBytes);

        String fileHash = calculateSha256(originalBytes);
        return new PreparedImportFile(
                originalFileName,
                originalBytes,
                fileHash,
                originalBytes.length
        );
    }

    public ParsedImportFile parse(PreparedImportFile preparedFile) {
        if (preparedFile == null) {
            throw new IllegalArgumentException(
                    "preparedFile must not be null"
            );
        }
        byte[] originalBytes = preparedFile.originalBytes();
        String decoded = decodeStrictUtf8(originalBytes);
        rejectBareCarriageReturns(decoded);
        List<ParsedCsvRow> rows = parseRecords(decoded);

        return new ParsedImportFile(
                preparedFile.originalFileName(),
                preparedFile.fileHash(),
                preparedFile.fileSizeBytes(),
                rows
        );
    }

    private void validateRequest(
            String originalFileName,
            byte[] originalBytes) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw requestFailure(
                    ImportFileRequestErrorCode.MISSING_FILE_NAME,
                    "File name is required"
            );
        }
        if (originalFileName.length() > 255) {
            throw requestFailure(
                    ImportFileRequestErrorCode.INVALID_FILE_NAME,
                    "File name must not exceed 255 characters"
            );
        }
        if (!originalFileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw requestFailure(
                    ImportFileRequestErrorCode.INVALID_FILE_EXTENSION,
                    "File extension must be .csv"
            );
        }
        if (originalBytes == null || originalBytes.length == 0) {
            throw requestFailure(
                    ImportFileRequestErrorCode.EMPTY_FILE,
                    "File must not be empty"
            );
        }
        if (originalBytes.length > MAX_FILE_SIZE_BYTES) {
            throw requestFailure(
                    ImportFileRequestErrorCode.FILE_TOO_LARGE,
                    "File must not exceed 5 MiB"
            );
        }
    }

    private String calculateSha256(byte[] originalBytes) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    messageDigest.digest(originalBytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available in this Java runtime"
            );
        }
    }

    private String decodeStrictUtf8(byte[] originalBytes) {
        int offset = startsWithUtf8Bom(originalBytes)
                ? UTF_8_BOM.length
                : 0;
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(
                            originalBytes,
                            offset,
                            originalBytes.length - offset
                    ))
                    .toString();
            if (decoded.indexOf('\uFEFF') >= 0) {
                throw fileFailure(
                        ImportFileErrorCode.INVALID_UTF8,
                        "File must be valid UTF-8 with an optional leading BOM"
                );
            }
            return decoded;
        } catch (CharacterCodingException exception) {
            throw fileFailure(
                    ImportFileErrorCode.INVALID_UTF8,
                    "File must be valid UTF-8 with an optional leading BOM"
            );
        }
    }

    private boolean startsWithUtf8Bom(byte[] bytes) {
        if (bytes.length < UTF_8_BOM.length) {
            return false;
        }
        for (int index = 0; index < UTF_8_BOM.length; index++) {
            if (bytes[index] != UTF_8_BOM[index]) {
                return false;
            }
        }
        return true;
    }

    private void rejectBareCarriageReturns(String decoded) {
        for (int index = 0; index < decoded.length(); index++) {
            if (decoded.charAt(index) == '\r'
                    && (index + 1 >= decoded.length()
                    || decoded.charAt(index + 1) != '\n')) {
                throw fileFailure(
                        ImportFileErrorCode.MALFORMED_CSV,
                        "CSV structure is malformed"
                );
            }
        }
    }

    private List<ParsedCsvRow> parseRecords(String decoded) {
        try (CSVParser parser = CSVParser.parse(decoded, CSV_FORMAT)) {
            Iterator<CSVRecord> records = parser.iterator();
            if (!records.hasNext()) {
                throw fileFailure(
                        ImportFileErrorCode.INVALID_HEADER,
                        "CSV header does not match the required six-column header"
                );
            }

            validateHeader(records.next());

            List<ParsedCsvRow> rows = new ArrayList<>();
            while (records.hasNext()) {
                if (rows.size() == MAX_DATA_ROWS) {
                    throw fileFailure(
                            ImportFileErrorCode.TOO_MANY_ROWS,
                            "CSV file must not contain more than 10000 data rows"
                    );
                }

                CSVRecord record = records.next();
                List<String> values = new ArrayList<>(record.size());
                for (String value : record) {
                    values.add(value);
                }
                rows.add(new ParsedCsvRow(
                        Math.toIntExact(record.getRecordNumber()),
                        values
                ));
            }
            if (rows.isEmpty()) {
                throw fileFailure(
                        ImportFileErrorCode.NO_DATA_ROWS,
                        "CSV file must contain at least one data row"
                );
            }
            return rows;
        } catch (IOException | UncheckedIOException exception) {
            throw fileFailure(
                    ImportFileErrorCode.MALFORMED_CSV,
                    "CSV structure is malformed"
            );
        }
    }

    private void validateHeader(CSVRecord header) {
        if (header.size() != REQUIRED_HEADER.size()) {
            throw invalidHeader();
        }
        for (int index = 0; index < REQUIRED_HEADER.size(); index++) {
            if (!REQUIRED_HEADER.get(index).equals(header.get(index))) {
                throw invalidHeader();
            }
        }
    }

    private InvalidImportFileRequestException requestFailure(
            ImportFileRequestErrorCode errorCode,
            String message) {
        return new InvalidImportFileRequestException(errorCode, message);
    }

    private ImportFileParseException invalidHeader() {
        return fileFailure(
                ImportFileErrorCode.INVALID_HEADER,
                "CSV header does not match the required six-column header"
        );
    }

    private ImportFileParseException fileFailure(
            ImportFileErrorCode errorCode,
            String message) {
        return new ImportFileParseException(errorCode, message);
    }
}
