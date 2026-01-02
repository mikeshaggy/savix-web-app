package com.mikeshaggy.backend;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CsvImportService {

    private static final String EXPECTED_HEADERS = "id, wallet_id, category_id, title, amount, transaction_date, " +
            "notes, importance, cycle, created_at";

    public void importCsv(MultipartFile file) throws IOException {
        CSVFormat format = getFormat();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                CSVParser csvParser = format.parse(reader)) {

            List<String> headers = csvParser.getHeaderNames();
            System.out.println(validateHeaders(headers));
            System.out.println("headers: " + headers);
            List<CSVRecord> records = csvParser.getRecords();
            System.out.println("records size: " + records.size());
            for (CSVRecord record : records) {
                System.out.println("Record #: " + record.getRecordNumber());
                for (String header : headers) {
                    System.out.print(record.get(header) + " | ");
                }
                System.out.println();
            }
        }
    }

    private CSVFormat getFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(false)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
    }

    private boolean validateHeaders(List<String> headers) {
        String joinedHeaders = String.join(", ", headers);
        return EXPECTED_HEADERS.equalsIgnoreCase(joinedHeaders);
    }
}
