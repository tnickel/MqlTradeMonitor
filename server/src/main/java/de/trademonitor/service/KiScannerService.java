package de.trademonitor.service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.trademonitor.dto.KiScannerDtos;
import de.trademonitor.entity.KiDocumentEntity;
import de.trademonitor.entity.KiSignalEntity;
import de.trademonitor.entity.KiSyncRunEntity;
import de.trademonitor.repository.KiDocumentRepository;
import de.trademonitor.repository.KiSignalRepository;
import de.trademonitor.repository.KiSyncRunRepository;

/**
 * Business logic of the MqlKiScanner sync protocol v1.
 *
 * The scanner is a one-shot client: register → signals → documents →
 * complete, then the connection is closed. There is no heartbeat and no
 * persistent session — the dashboard tile therefore reports the latest
 * run ("MqlKiScanner verbunden" after every completed sync).
 */
@Service
public class KiScannerService {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(KiScannerService.class.getName());

    /** Sync protocol version this server speaks. */
    public static final int PROTOCOL_VERSION = 1;
    public static final String CLIENT_NAME = "MqlKiScanner";
    public static final String API_VERSION = "v1";

    /** Hard cap per document (base64-decoded bytes); nginx allows 10m requests. */
    private static final int MAX_DOCUMENT_BYTES = 12 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");
    private static final Set<String> ALLOWED_GROUPS = Set.of("eigene", "downloader", "portfolio");

    @Autowired
    private KiSignalRepository kiSignalRepository;

    @Autowired
    private KiDocumentRepository kiDocumentRepository;

    @Autowired
    private KiSyncRunRepository kiSyncRunRepository;

    /** Currently open run: the protocol allows exactly one scanner at a time. */
    private KiSyncRunEntity openRun;

    @Transactional
    public Map<String, Object> register(KiScannerDtos.RegisterRequest request) {
        KiSyncRunEntity run = new KiSyncRunEntity(
                request != null ? request.getScannerVersion() : null);
        run = kiSyncRunRepository.save(run);
        openRun = run;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("runId", run.getId());
        response.put("serverTime", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.put("documents", documentInventory());
        response.put("signalIds", kiSignalRepository.findAll().stream()
                .map(KiSignalEntity::getSignalId).toList());
        LOG.info("[KISCANNER] Sync run " + run.getId() + " registered (scanner version "
                + run.getScannerVersion() + ")");
        return response;
    }

    /** docKey + sha256 of every stored document — the client-side diff basis. */
    public List<Map<String, Object>> documentInventory() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (KiDocumentEntity doc : kiDocumentRepository.findAll()) {
            Map<String, Object> item = new HashMap<>();
            item.put("docKey", doc.getDocKey());
            item.put("sha256", doc.getSha256());
            items.add(item);
        }
        return items;
    }

    /** Replace the whole signal table with the sent snapshot (mirror semantics). */
    @Transactional
    public Map<String, Object> replaceSignals(List<KiScannerDtos.SignalRow> rows) {
        int stored = 0;
        Set<Long> seen = new HashSet<>();
        List<KiScannerDtos.SignalRow> safeRows = rows != null ? rows : List.of();
        for (int order = 0; order < safeRows.size(); order++) {
            KiScannerDtos.SignalRow row = safeRows.get(order);
            if (row == null || row.signalId == null || row.signalId <= 0) {
                continue;
            }
            KiSignalEntity entity = kiSignalRepository
                    .findBySignalId(row.signalId).orElse(new KiSignalEntity());
            entity.setSignalId(row.signalId);
            entity.setDisplayOrder(order);
            entity.setName(row.name != null ? row.name : "");
            entity.setPlatform(row.platform);
            entity.setUrl(row.url);
            entity.setAmpel(row.ampel);
            entity.setScore(row.score);
            entity.setUrteil(trim(row.urteil, 1024));
            entity.setKurzfassung(row.kurzfassung);
            entity.setStopNachweis(trim(row.stop, 512));
            entity.setStopEvidence(row.stopEvidence);
            entity.setTradingDdPct(row.tradingDdPct);
            entity.setDdEquityPct(row.ddEquityPct);
            entity.setDdBalancePct(row.ddBalancePct);
            entity.setErtragMonatPct(row.ertragMonatPct);
            entity.setGrowthPct(row.growthPct);
            entity.setPf(row.pf);
            entity.setWinratePct(row.winratePct);
            entity.setAboPreisUsd(row.aboPreisUsd);
            entity.setAbonnenten(row.abonnenten);
            entity.setWochen(row.wochen);
            entity.setAboDelta7(row.aboDelta7);
            entity.setAboDelta30(row.aboDelta30);
            entity.setAboStand(row.aboStand);
            entity.setMartingale(row.martingale);
            entity.setPeakPositionen(row.peakPositionen);
            entity.setPeakNettoLots(row.peakNettoLots);
            entity.setShockUsd(row.shockUsd);
            entity.setKapitalbasisUsd(row.kapitalbasisUsd);
            entity.setBrokerServer(row.brokerServer);
            entity.setSymbole(trim(row.symbole, 1024));
            entity.setBerichtVom(row.berichtVom);
            entity.setDocsBerichte(row.docsBerichte);
            entity.setDocsTiefenanalyse(row.docsTiefenanalyse);
            entity.setDocsDownloader(row.docsDownloader);
            entity.setTradesSha256(row.tradesSha256);
            entity.setStand(row.stand);
            entity.setUpdatedAt(LocalDateTime.now());
            kiSignalRepository.save(entity);
            seen.add(row.signalId);
            stored++;
        }
        // Mirror semantics: rows missing from the snapshot are removed.
        List<KiSignalEntity> stale = new ArrayList<>();
        for (KiSignalEntity existing : kiSignalRepository.findAll()) {
            if (!seen.contains(existing.getSignalId())) {
                stale.add(existing);
            }
        }
        kiSignalRepository.deleteAllInBatch(stale);
        // Die endgültige Bilanz liefert complete() — hier nur zählen.
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("stored", stored);
        response.put("deleted", stale.size());
        return response;
    }

    /** Store one document (upsert by docKey); validates type and size. */
    @Transactional
    public Map<String, Object> storeDocument(KiScannerDtos.DocumentRequest request)
            throws KiScannerProtocolException {
        if (request == null || isBlank(request.getDocKey())
                || isBlank(request.getFileName()) || isBlank(request.getContentBase64())) {
            throw new KiScannerProtocolException(
                    "docKey, fileName und contentBase64 sind Pflichtfelder");
        }
        if (request.getDocKey().length() > 512 || request.getFileName().length() > 255) {
            throw new KiScannerProtocolException("docKey/fileName zu lang");
        }
        String contentType = request.getContentType() != null
                ? request.getContentType() : "application/pdf";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new KiScannerProtocolException(
                    "Content-Type nicht erlaubt: " + contentType + " (erlaubt: PDF)");
        }
        String group = isBlank(request.getGroup()) ? "eigene" : request.getGroup();
        if (!ALLOWED_GROUPS.contains(group)) {
            throw new KiScannerProtocolException("Unbekannte Gruppe: " + group);
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(request.getContentBase64());
        } catch (IllegalArgumentException e) {
            throw new KiScannerProtocolException("contentBase64 ist kein gültiges Base64");
        }
        if (data.length == 0) {
            throw new KiScannerProtocolException("Dokument ist leer");
        }
        if (data.length > MAX_DOCUMENT_BYTES) {
            throw new KiScannerProtocolException("Dokument zu groß: " + data.length
                    + " Bytes (Limit " + MAX_DOCUMENT_BYTES + ")");
        }
        // Verify the content actually looks like the announced type.
        if (!looksLikePdf(data)) {
            throw new KiScannerProtocolException(
                    "Inhalt entspricht nicht dem angekündigten Typ (PDF-Magic fehlt)");
        }
        String sha256 = request.getSha256() != null && request.getSha256().length() == 64
                ? request.getSha256().toLowerCase()
                : sha256Hex(data);

        KiDocumentEntity entity = kiDocumentRepository
                .findByDocKey(request.getDocKey()).orElse(new KiDocumentEntity());
        entity.setDocKey(request.getDocKey());
        entity.setSignalId(request.getSignalId());
        entity.setGroup(group);
        entity.setKind(request.getKind());
        entity.setLabel(trim(request.getLabel(), 255));
        entity.setFileName(request.getFileName());
        entity.setContentType(contentType);
        entity.setFileSize(data.length);
        entity.setSha256(sha256);
        entity.setLastModified(request.getLastModified());
        entity.setFileData(data);
        entity.setUpdatedAt(LocalDateTime.now());
        kiDocumentRepository.save(entity);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("docKey", entity.getDocKey());
        response.put("stored", true);
        return response;
    }

    @Transactional
    public Map<String, Object> complete(KiScannerDtos.SyncStatsRequest stats) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        if (openRun == null) {
            response.put("runId", null);
            return response;
        }
        if (stats != null) {
            if (stats.getSignals() != null) {
                openRun.setSignalsStored(stats.getSignals());
            }
            openRun.setDocumentsTotal(stats.getDocuments() != null ? stats.getDocuments() : 0);
            openRun.setDocumentsUploaded(stats.getUploaded() != null ? stats.getUploaded() : 0);
            openRun.setDocumentsSkipped(stats.getSkipped() != null ? stats.getSkipped() : 0);
            openRun.setBytesTransferred(stats.getBytes() != null ? stats.getBytes() : 0L);
        }
        openRun.setStatus("ok");
        openRun.setFinishedAt(LocalDateTime.now());
        kiSyncRunRepository.save(openRun);
        response.put("runId", openRun.getId());
        LOG.info("[KISCANNER] Sync run " + openRun.getId() + " completed: "
                + openRun.getSignalsStored() + " signals, "
                + openRun.getDocumentsUploaded() + " documents uploaded");
        openRun = null;
        return response;
    }

    @Transactional
    public Map<String, Object> abort(String reason) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        if (openRun == null) {
            response.put("runId", null);
            return response;
        }
        openRun.setStatus("aborted");
        openRun.setNote(trim(reason, 1024));
        openRun.setFinishedAt(LocalDateTime.now());
        kiSyncRunRepository.save(openRun);
        response.put("runId", openRun.getId());
        LOG.warning("[KISCANNER] Sync run " + openRun.getId() + " aborted: " + reason);
        openRun = null;
        return response;
    }

    /** Aggregate state for the dashboard tile and the /kiscanner page header. */
    public Map<String, Object> statusForDashboard() {
        Map<String, Object> status = new LinkedHashMap<>();
        KiSyncRunEntity last = kiSyncRunRepository.findTopByOrderByIdDesc().orElse(null);
        boolean hasData = last != null && "ok".equals(last.getStatus());
        status.put("connected", hasData);
        if (last != null) {
            status.put("lastRunId", last.getId());
            status.put("lastRunStatus", last.getStatus());
            LocalDateTime lastAt = last.getFinishedAt() != null
                    ? last.getFinishedAt() : last.getStartedAt();
            status.put("lastRunAt", lastAt);
            status.put("lastRunAtText",
                    lastAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
            status.put("lastRunSignals", last.getSignalsStored());
            status.put("lastRunDocuments", last.getDocumentsUploaded());
            status.put("scannerVersion", last.getScannerVersion());
        }
        status.put("signals", kiSignalRepository.count());
        status.put("documents", kiDocumentRepository.count());
        return status;
    }

    public List<KiSignalEntity> signalsForView() {
        return kiSignalRepository.findAllByOrderByDisplayOrderAsc();
    }

    public List<KiDocumentEntity> documentsForSignal(long signalId) {
        return kiDocumentRepository.findBySignalIdOrderByKindAscFileNameAsc(signalId);
    }

    public List<KiDocumentEntity> portfolioDocuments() {
        return kiDocumentRepository.findByGroupOrderByUpdatedAtDesc("portfolio");
    }

    public KiDocumentEntity document(long id) {
        return kiDocumentRepository.findById(id).orElse(null);
    }

    // --- Helpers -----------------------------------------------------------

    /** Protocol violation → HTTP 400 with a helpful message for the client. */
    public static class KiScannerProtocolException extends Exception {
        public KiScannerProtocolException(String message) {
            super(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean looksLikePdf(byte[] data) {
        return data.length > 4 && data[0] == '%' && data[1] == 'P'
                && data[2] == 'D' && data[3] == 'F';
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
