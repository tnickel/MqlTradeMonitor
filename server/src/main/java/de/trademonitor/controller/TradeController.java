package de.trademonitor.controller;

import de.trademonitor.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final ExportService exportService;

    public TradeController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportAllTrades() {
        String csv = exportService.exportAllTradesToCsv();
        byte[] content = csv.getBytes();

        String filename = "trades_export_all_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/export/csv/{accountId}")
    public ResponseEntity<byte[]> exportAccountTrades(@PathVariable long accountId) {
        String csv = exportService.exportAccountTradesToCsv(accountId);
        byte[] content = csv.getBytes();

        String filename = "trades_export_" + accountId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }
}
