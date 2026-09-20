package de.trademonitor.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.trademonitor.dto.KiScannerDtos;
import de.trademonitor.repository.UserRepository;
import de.trademonitor.service.KiScannerService;

/**
 * REST API for the MqlKiScanner sync protocol v1 (special client).
 *
 * The MqlKiScanner is a Python/Streamlit analysis tool, not a MetaTrader
 * EA — it does not use /api/register + trades. It gets its own one-shot
 * protocol under /api/kiscanner (see Doku/MqlKiScanner_Integration.md):
 *
 *   1. POST /api/kiscanner/register   handshake, opens a sync run
 *   2. POST /api/kiscanner/signals    full signal-table snapshot
 *   3. POST /api/kiscanner/documents  one PDF per call (base64)
 *   4. POST /api/kiscanner/complete   closes the run, connection ends
 *      POST /api/kiscanner/abort      error path
 *
 * Sonderbehandlung: every call must carry a valid server API key in the
 * X-User-Key header (any user account's key, like the EAs) AND the
 * handshake must identify itself as client "MqlKiScanner" speaking
 * protocol version 1. The browser endpoints /status and /documents/{id}/view
 * are session-authenticated (Spring Security) and serve the dashboard tile
 * and the /kiscanner page.
 */
@RestController
@RequestMapping("/api/kiscanner")
public class KiScannerApiController {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(KiScannerApiController.class.getName());

    @Autowired
    private KiScannerService kiScannerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private de.trademonitor.repository.ClientErrorLogRepository clientErrorLogRepository;

    /** True iff the X-User-Key matches an existing user's API key. */
    private boolean isAuthorized(String userKey) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return false;
        }
        return userRepository.findByApiKey(userKey.trim()).isPresent();
    }

    private ResponseEntity<Map<String, Object>> unauthorized(
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            clientErrorLogRepository.save(new de.trademonitor.entity.ClientErrorLog(
                    0L, "KISCANNER_AUTH_FAILED", request.getRemoteAddr(),
                    "Invalid or missing X-User-Key for KiScanner sync"));
        } catch (Exception e) {
            LOG.warning("Failed to save client error log: " + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message",
                        "Unauthorized — gültigen API-Key im Header X-User-Key mitsenden"));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", message));
    }

    /** Connection test for the scanner's status badge (erreichbar + Key ok). */
    @GetMapping("/ping")
    public ResponseEntity<?> ping(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!isAuthorized(userKey)) {
            return unauthorized(httpRequest);
        }
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "MqlTradeMonitor",
                "kiscannerApi", KiScannerService.API_VERSION,
                "protocolVersion", KiScannerService.PROTOCOL_VERSION));
    }

    /** Step 1: handshake. Validates the special client identification. */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody(required = false) KiScannerDtos.RegisterRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!isAuthorized(userKey)) {
            return unauthorized(httpRequest);
        }
        if (request == null || !KiScannerService.CLIENT_NAME.equals(request.getClient())) {
            return badRequest("Client-Kennung fehlt oder falsch — Pflichtfeld "
                    + "client=\"" + KiScannerService.CLIENT_NAME + "\" (Sonderprotokoll "
                    + "für den MqlKiScanner, nicht der EA-Register-Weg).");
        }
        if (request.getProtocolVersion() == null
                || request.getProtocolVersion() != KiScannerService.PROTOCOL_VERSION) {
            return badRequest("Protokollversion " + request.getProtocolVersion()
                    + " nicht unterstützt — Server spricht Version "
                    + KiScannerService.PROTOCOL_VERSION + ".");
        }
        return ResponseEntity.ok(kiScannerService.register(request));
    }

    /** Step 2: full signal-table snapshot (mirror semantics). */
    @PostMapping("/signals")
    public ResponseEntity<?> signals(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody(required = false) KiScannerDtos.SignalsRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!isAuthorized(userKey)) {
            return unauthorized(httpRequest);
        }
        if (request == null || request.getSignals() == null) {
            return badRequest("Payload fehlt: {\"signals\": [...]}");
        }
        if (request.getSignals().size() > 2000) {
            return badRequest("Zu viele Signale in einem Snapshot (Limit 2000)");
        }
        return ResponseEntity.ok(
                kiScannerService.replaceSignals(request.getSignals()));
    }

    /** Step 3: one document per call (PDF, base64). */
    @PostMapping("/documents")
    public ResponseEntity<?> document(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody(required = false) KiScannerDtos.DocumentRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!isAuthorized(userKey)) {
            return unauthorized(httpRequest);
        }
        try {
            return ResponseEntity.ok(kiScannerService.storeDocument(request));
        } catch (KiScannerService.KiScannerProtocolException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            LOG.severe("[KISCANNER] document store failed: " + msg);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", msg));
        }
    }

    /** Step 4a: close the run — connection ends here. */
    @PostMapping("/complete")
    public ResponseEntity<?> complete(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody(required = false) KiScannerDtos.SyncStatsRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!isAuthorized(userKey)) {
            return unauthorized(httpRequest);
        }
        return ResponseEntity.ok(kiScannerService.complete(request));
    }

    /** Step 4b: error path — mark the run as aborted. */
    @PostMapping("/abort")
    public ResponseEntity<?> abort(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody(required = false) KiScannerDtos.AbortRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (!isAuthorized(userKey)) {
            return unauthorized(httpRequest);
        }
        return ResponseEntity.ok(
                kiScannerService.abort(request != null ? request.getReason() : ""));
    }

    // --- Browser endpoints (session-authenticated via Spring Security) -----

    /** Live status for the dashboard tile (polled every 30 s). */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(kiScannerService.statusForDashboard());
    }

    /** Inline PDF viewer endpoint for the /kiscanner page. */
    @GetMapping("/documents/{documentId}/view")
    public ResponseEntity<?> viewDocument(@PathVariable long documentId) {
        de.trademonitor.entity.KiDocumentEntity doc = kiScannerService.document(documentId);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        String storedType = doc.getContentType();
        boolean inline = "application/pdf".equals(storedType);
        org.springframework.http.ContentDisposition disposition = (inline
                ? org.springframework.http.ContentDisposition.inline()
                : org.springframework.http.ContentDisposition.attachment())
                .filename(doc.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                        inline ? storedType : "application/octet-stream")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(doc.getFileData());
    }
}
