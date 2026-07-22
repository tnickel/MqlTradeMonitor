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

import de.trademonitor.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final ExportService exportService;

    public TradeController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportAllTrades(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String csv;
        if ("ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            csv = exportService.exportAllTradesToCsv();
        } else {
            java.util.Set<Long> allowedIds = userDetails.getUserEntity().getAllowedAccountIds();
            csv = exportService.exportUserTradesToCsv(allowedIds);
        }

        byte[] content = csv.getBytes();
        String filename = "trades_export_all_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/export/csv/{accountId}")
    public ResponseEntity<byte[]> exportAccountTrades(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        java.util.Set<Long> allowedIds = userDetails.getUserEntity().getAllowedAccountIds();
        if (!isAdmin && (allowedIds == null || !allowedIds.contains(accountId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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
