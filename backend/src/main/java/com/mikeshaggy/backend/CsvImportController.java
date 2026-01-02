package com.mikeshaggy.backend;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping(CsvImportController.BASE_URL)
public class CsvImportController {

    public static final String BASE_URL = "/api/csv-import";
    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @PostMapping(
            value = "/csv",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        csvImportService.importCsv(file);
        return ResponseEntity.accepted().build();
    }
}
