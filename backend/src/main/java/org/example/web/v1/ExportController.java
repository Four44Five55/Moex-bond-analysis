package org.example.web.v1;

import org.example.application.ExportService;
import org.example.application.InstrumentService;
import org.example.domain.filter.FilterCriteria;
import org.example.domain.model.InstrumentType;
import org.example.web.dto.InstrumentDto;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InstrumentService instrumentService;
    private final ExportService exportService;

    public ExportController(InstrumentService instrumentService, ExportService exportService) {
        this.instrumentService = instrumentService;
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "BOND") InstrumentType type,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") double minYield,
            @RequestParam(defaultValue = "100") double maxDuration,
            @RequestParam(defaultValue = "1") int minReliability,
            @RequestParam(defaultValue = "false") boolean onlyInvestmentGrade,
            @RequestParam(defaultValue = "true") boolean excludeSuspicious,
            @RequestParam(required = false) String sector
    ) {
        FilterCriteria criteria = FilterCriteria.builder()
                .type(type)
                .query(query)
                .minYield(minYield)
                .maxDuration(maxDuration)
                .minReliabilityScore(minReliability)
                .onlyInvestmentGrade(onlyInvestmentGrade)
                .excludeSuspicious(excludeSuspicious)
                .sector(sector)
                .build();

        List<InstrumentDto> rows = instrumentService.findAllForExport(criteria);
        byte[] body = exportService.toXlsx(rows, type);

        String filename = type == InstrumentType.SHARE ? "moex_shares.xlsx" : "moex_bonds.xlsx";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build();

        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(body);
    }
}
