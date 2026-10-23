package de.trademonitor.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import de.trademonitor.dto.KiScannerDtos;
import de.trademonitor.entity.*;
import de.trademonitor.repository.*;

/** v1 compatibility and run-bound, atomic composite-identity v2 snapshots. */
@Service
public class KiScannerService {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(KiScannerService.class.getName());
    public static final int PROTOCOL_VERSION = 2;
    public static final String CLIENT_NAME = "MqlKiScanner";
    public static final int API_VERSION = 2;
    public static final String LEGACY_UID = "legacy:v1";
    private static final int MAX_DOCUMENT_BYTES = 12 * 1024 * 1024;
    private static final long MAX_STAGED_BYTES = 512L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");
    private static final Set<String> ALLOWED_GROUPS = Set.of("eigene", "downloader", "portfolio");
    @Autowired private KiSignalRepository kiSignalRepository;
    @Autowired private KiDocumentRepository kiDocumentRepository;
    @Autowired private KiSyncRunRepository kiSyncRunRepository;
    @Autowired private KiScannerSchemaMigration schema;
    @Autowired private TransactionTemplate transaction;

    private KiSyncRunEntity openRun;
    private int openVersion = 1;
    private String ownerHash;
    private Integer expectedSignals;
    private List<KiScannerDtos.SignalRow> stagedSignals;
    private final Map<String, KiDocumentEntity> stagedDocuments = new LinkedHashMap<>();

    public synchronized Map<String, Object> register(KiScannerDtos.RegisterRequest request, String key) {
        int version = request.getProtocolVersion();
        if (request.getSignalCount() != null && (request.getSignalCount() < 0 || request.getSignalCount() > 2000))
            throw new KiScannerProtocolException("signalCount ausserhalb 0..2000");
        if (version == 1 && schema.v2Active())
            throw new KiScannerProtocolException("V2-Bestand aktiv; v1 darf ihn nicht ueberschreiben");
        if (openRun != null && openVersion == 2) {
            if (openRun.getStartedAt().isAfter(LocalDateTime.now().minusMinutes(30)))
                throw new KiScannerProtocolException("Ein v2-Sync ist bereits offen; zuerst complete/abort");
            finishRun("aborted", "Abgelaufener v2-Sync, Bestand unveraendert");
        }
        openRun = kiSyncRunRepository.save(new KiSyncRunEntity(request.getScannerVersion()));
        openVersion = version;
        ownerHash = sha256Hex(key.getBytes(StandardCharsets.UTF_8));
        expectedSignals = request.getSignalCount();
        stagedSignals = null;
        stagedDocuments.clear();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("runId", openRun.getId());
        response.put("protocolVersion", version);
        response.put("supportedProtocolVersions", List.of(1, 2));
        response.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        response.put("documents", documentInventory());
        response.put("signalIds", kiSignalRepository.findAll().stream().map(KiSignalEntity::getSignalId).toList());
        response.put("signalIdentities", kiSignalRepository.findAll().stream().map(signal -> Map.of(
                "sourceUid", signal.getSourceUid(), "source", signal.getSource(), "signalId", signal.getSignalId())).toList());
        return response;
    }

    public List<Map<String, Object>> documentInventory() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (KiDocumentEntity doc : kiDocumentRepository.findAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docKey", doc.getDocKey()); item.put("sha256", doc.getSha256());
            item.put("sourceUid", doc.getSourceUid()); item.put("source", doc.getSource());
            item.put("signalId", doc.getSignalId());
            items.add(item);
        }
        return items;
    }

    private void validateRun(Long runId, String key) {
        if (openRun == null) throw new KiScannerProtocolException("Zuerst register aufrufen");
        if (runId != null) {
            if (openRun == null || openVersion != 2 || !runId.equals(openRun.getId())
                    || !sha256Hex(key.getBytes(StandardCharsets.UTF_8)).equals(ownerHash))
                throw new KiScannerProtocolException("Unbekannte oder fremde v2-runId");
        } else if (openRun != null && openVersion == 2) {
            throw new KiScannerProtocolException("runId ist fuer v2-Schreibaufrufe Pflicht");
        } else if (schema.v2Active()) {
            throw new KiScannerProtocolException("V2-Bestand aktiv; v1-Schreibaufruf abgewiesen");
        }
    }

    public synchronized Map<String, Object> replaceSignals(KiScannerDtos.SignalsRequest request, String key) {
        validateRun(request.runId, key);
        List<KiScannerDtos.SignalRow> rows = request.getSignals();
        if (request.runId != null) {
            if (!stagedDocuments.isEmpty()) throw new KiScannerProtocolException("Signals nach Dokumenten nicht mehr aenderbar");
            Set<String> seen = new HashSet<>();
            for (KiScannerDtos.SignalRow row : rows) {
                if (row == null || row.signalId == null || row.signalId <= 0)
                    throw new KiScannerProtocolException("Jedes Signal braucht eine positive signalId");
                validateSource(row.sourceUid, row.source);
                if (!seen.add(identity(row.sourceUid, row.signalId)))
                    throw new KiScannerProtocolException("Doppelte Signalidentitaet im Snapshot");
            }
            stagedSignals = List.copyOf(rows);
            return Map.of("status", "ok", "stored", rows.size(), "deleted", 0, "staged", true);
        }
        return transaction.execute(status -> persistRows(rows, false));
    }

    private Map<String, Object> persistRows(List<KiScannerDtos.SignalRow> rows, boolean v2) {
        Set<String> seen = new HashSet<>(); int stored = 0;
        for (int order = 0; order < rows.size(); order++) {
            KiScannerDtos.SignalRow row = rows.get(order);
            if (row == null || row.signalId == null || row.signalId <= 0) continue;
            String uid = v2 ? row.sourceUid : LEGACY_UID;
            KiSignalEntity entity = kiSignalRepository.findBySourceUidAndSignalId(uid, row.signalId).orElse(new KiSignalEntity());
            entity.setSignalId(row.signalId); entity.setSourceUid(uid);
            entity.setSource(v2 ? row.source : "Legacy v1");
            mapRow(entity, row, order);
            seen.add(identity(uid, row.signalId)); stored++;
        }
        List<KiSignalEntity> stale = kiSignalRepository.findAll().stream()
                .filter(row -> !seen.contains(identity(row.getSourceUid(), row.getSignalId()))).toList();
        kiSignalRepository.deleteAllInBatch(stale);
        return Map.of("status", "ok", "stored", stored, "deleted", stale.size());
    }

    private void mapRow(KiSignalEntity entity, KiScannerDtos.SignalRow row, int order) {
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
        entity.setMaxDrawdownEquityPct(nonNegative(row.maxDrawdownEquityPct));
        entity.setDrawdownLimitPct(positive(row.drawdownLimitPct));
        entity.setMinReturnMonthlyPct(finite(row.minReturnMonthlyPct));
        entity.setMinRetddMonthly(nonNegative(row.minRetddMonthly));
        entity.setMinCalmarJahr(nonNegative(row.minCalmarJahr));
        entity.setRetddJahrVorbehalt(finite(row.retddJahrVorbehalt));
        entity.setRetddVorbehaltGrund(trim(row.retddVorbehaltGrund, 2048));
        entity.setEquityMeasurementStatus(trim(row.equityMeasurementStatus, 2048));
        entity.setErtragMonatPct(row.ertragMonatPct);
        entity.setErtragMonatForensikPct(row.ertragMonatForensikPct);
        entity.setErtragMonatGeomPct(finite(row.ertragMonatGeomPct));
        entity.setCagrJahrPct(finite(row.cagrJahrPct));
        entity.setRetddMonat(finite(row.retddMonat));
        entity.setRetddJahr(finite(row.retddJahr));
        entity.setRetddBasis(trim(row.retddBasis, 128));
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
    }

    public synchronized Map<String, Object> storeDocument(KiScannerDtos.DocumentRequest request, String key) {
        if (request == null) throw new KiScannerProtocolException("Dokument fehlt");
        validateRun(request.runId, key);
        KiDocumentEntity entity = prepareDocument(request);
        if (request.runId != null) {
            validateDocumentOwner(entity);
            KiDocumentEntity prior = stagedDocuments.get(entity.getDocKey());
            if (prior == null) prior = kiDocumentRepository.findByDocKey(entity.getDocKey()).orElse(null);
            if (prior != null && !sameOwner(prior, entity))
                throw new KiScannerProtocolException("docKey darf seinen Besitzer nicht wechseln");
            long bytes = stagedDocuments.values().stream().mapToLong(KiDocumentEntity::getFileSize).sum();
            KiDocumentEntity previous = stagedDocuments.get(entity.getDocKey());
            if (stagedDocuments.size() >= 10000 || bytes + entity.getFileSize()
                    - (previous == null ? 0 : previous.getFileSize()) > MAX_STAGED_BYTES)
                throw new KiScannerProtocolException("Staging-Limit erreicht");
            stagedDocuments.put(entity.getDocKey(), entity);
        } else {
            kiDocumentRepository.findByDocKey(entity.getDocKey()).ifPresent(old -> entity.setId(old.getId()));
            kiDocumentRepository.save(entity);
        }
        return Map.of("status", "ok", "docKey", entity.getDocKey(), "stored", true);
    }

    private KiDocumentEntity prepareDocument(KiScannerDtos.DocumentRequest request) {
        if (isBlank(request.getDocKey()) || isBlank(request.getFileName()) || isBlank(request.getContentBase64()))
            throw new KiScannerProtocolException("docKey, fileName und contentBase64 sind Pflichtfelder");
        if (request.getDocKey().length() > 512 || request.getFileName().length() > 255)
            throw new KiScannerProtocolException("docKey/fileName zu lang");
        String contentType = request.getContentType() == null ? "application/pdf" : request.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) throw new KiScannerProtocolException("Nur PDF erlaubt");
        String group = isBlank(request.getGroup()) ? "eigene" : request.getGroup();
        if (!ALLOWED_GROUPS.contains(group)) throw new KiScannerProtocolException("Unbekannte Gruppe");
        byte[] data;
        try { data = Base64.getDecoder().decode(request.getContentBase64()); }
        catch (IllegalArgumentException e) { throw new KiScannerProtocolException("Ungueltiges Base64"); }
        if (data.length == 0 || data.length > MAX_DOCUMENT_BYTES || !looksLikePdf(data))
            throw new KiScannerProtocolException("PDF-Magic oder Dokumentgroesse ungueltig");
        String sha = sha256Hex(data);
        if (!isBlank(request.getSha256()) && !sha.equalsIgnoreCase(request.getSha256()))
            throw new KiScannerProtocolException("SHA-256 stimmt nicht mit PDF-Inhalt ueberein");
        if (request.getSizeBytes() != null && request.runId != null && request.getSizeBytes() != data.length)
            throw new KiScannerProtocolException("sizeBytes stimmt nicht mit PDF-Inhalt ueberein");
        KiDocumentEntity entity = new KiDocumentEntity();
        entity.setDocKey(request.getDocKey()); entity.setSignalId(request.getSignalId());
        entity.setSourceUid(request.runId != null ? request.sourceUid : request.getSignalId() == null ? null : LEGACY_UID);
        entity.setSource(request.runId != null ? request.source : request.getSignalId() == null ? null : "Legacy v1");
        entity.setGroup(group); entity.setKind(request.getKind()); entity.setLabel(trim(request.getLabel(), 255));
        entity.setFileName(request.getFileName()); entity.setContentType(contentType);
        entity.setFileSize(data.length); entity.setSha256(sha); entity.setLastModified(request.getLastModified());
        entity.setFileData(data); entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private void validateDocumentOwner(KiDocumentEntity doc) {
        if (stagedSignals == null) throw new KiScannerProtocolException("Signals-Snapshot muss zuerst eintreffen");
        if ("portfolio".equals(doc.getGroup())) {
            if (doc.getSignalId() != null || doc.getSourceUid() != null || doc.getSource() != null
                    || !doc.getDocKey().startsWith("portfolio/"))
                throw new KiScannerProtocolException("Portfolio hat keinen Signalbesitzer und braucht portfolio/-Key");
            return;
        }
        validateSource(doc.getSourceUid(), doc.getSource());
        if (doc.getSignalId() == null || doc.getSignalId() <= 0)
            throw new KiScannerProtocolException("Signaldokument braucht positive signalId");
        boolean ownerPresent = stagedSignals.stream().anyMatch(signal -> signal.signalId.equals(doc.getSignalId())
                && signal.sourceUid.equals(doc.getSourceUid()) && signal.source.equals(doc.getSource()));
        if (!ownerPresent) throw new KiScannerProtocolException("Dokumentbesitzer fehlt im aktuellen Snapshot");
        String prefix = "signal/" + encodeSegment(doc.getSourceUid()) + "/" + doc.getSignalId() + "/";
        if (!doc.getDocKey().startsWith(prefix) || doc.getDocKey().length() == prefix.length())
            throw new KiScannerProtocolException("docKey muss Signalbesitzer exakt qualifizieren");
    }

    public synchronized Map<String, Object> complete(KiScannerDtos.SyncStatsRequest stats, String key) {
        validateRun(stats == null ? null : stats.runId, key);
        if (openVersion == 2) {
            if (stagedSignals == null || stats == null || stats.documentKeys == null)
                throw new KiScannerProtocolException("Snapshot oder documentKeys fehlen");
            Set<String> keys = new HashSet<>(stats.documentKeys);
            if (keys.size() != stats.documentKeys.size() || keys.contains(null))
                throw new KiScannerProtocolException("Doppelte oder leere documentKeys");
            if (!keys.containsAll(stagedDocuments.keySet()))
                throw new KiScannerProtocolException("Hochgeladene Dokumente fehlen in documentKeys");
            List<KiDocumentEntity> documents = new ArrayList<>();
            for (String docKey : stats.documentKeys) {
                KiDocumentEntity doc = stagedDocuments.get(docKey);
                if (doc == null) doc = kiDocumentRepository.findByDocKey(docKey)
                        .orElseThrow(() -> new KiScannerProtocolException("Dokument fehlt: " + docKey));
                // Source labels may change; refresh cached documents only by their stable owner.
                if (doc.getSignalId() != null) {
                    String uid = doc.getSourceUid(); Long id = doc.getSignalId();
                    String currentLabel = stagedSignals.stream().filter(s -> s.sourceUid.equals(uid) && s.signalId.equals(id))
                            .map(s -> s.source).findFirst().orElse(null);
                    // Detached copy prevents read validation from updating committed entities.
                    KiDocumentEntity copy = copyDocument(doc);
                    copy.setSource(currentLabel); doc = copy;
                }
                validateDocumentOwner(doc); documents.add(doc);
            }
            long bytes = stagedDocuments.values().stream().mapToLong(KiDocumentEntity::getFileSize).sum();
            if (!Objects.equals(stats.getSignals(), stagedSignals.size())
                    || expectedSignals != null && expectedSignals != stagedSignals.size()
                    || !Objects.equals(stats.getDocuments(), keys.size())
                    || !Objects.equals(stats.getUploaded(), stagedDocuments.size())
                    || !Objects.equals(stats.getSkipped(), keys.size() - stagedDocuments.size())
                    || !Objects.equals(stats.getBytes(), bytes))
                throw new KiScannerProtocolException("Abschlusszaehler stimmen nicht mit Snapshot ueberein");
            Long id = openRun.getId();
            Map<String, Object> committed = transaction.execute(status -> {
                Map<String, Object> result = persistRows(stagedSignals, true);
                Map<String, KiDocumentEntity> existing = new HashMap<>();
                kiDocumentRepository.findAll().forEach(doc -> existing.put(doc.getDocKey(), doc));
                for (KiDocumentEntity doc : documents) {
                    KiDocumentEntity old = existing.remove(doc.getDocKey());
                    if (old != null) doc.setId(old.getId());
                    kiDocumentRepository.save(doc);
                }
                kiDocumentRepository.deleteAllInBatch(existing.values());
                schema.activateV2();
                applyStats(stats); finishRun("ok", null);
                return result;
            });
            clearRun();
            Map<String, Object> response = new LinkedHashMap<>(committed);
            response.put("runId", id);
            return response;
        }
        applyStats(stats); Long id = openRun.getId(); finishRun("ok", null); clearRun();
        return Map.of("status", "ok", "runId", id);
    }

    public synchronized Map<String, Object> abort(KiScannerDtos.AbortRequest request, String key) {
        validateRun(request == null ? null : request.runId, key);
        Long id = openRun.getId(); finishRun("aborted", request == null ? null : request.getReason()); clearRun();
        return Map.of("status", "ok", "runId", id);
    }

    private void applyStats(KiScannerDtos.SyncStatsRequest stats) {
        if (stats == null) return;
        openRun.setSignalsStored(stats.getSignals() == null ? 0 : stats.getSignals());
        openRun.setDocumentsTotal(stats.getDocuments() == null ? 0 : stats.getDocuments());
        openRun.setDocumentsUploaded(stats.getUploaded() == null ? 0 : stats.getUploaded());
        openRun.setDocumentsSkipped(stats.getSkipped() == null ? 0 : stats.getSkipped());
        openRun.setBytesTransferred(stats.getBytes() == null ? 0 : stats.getBytes());
    }
    private void finishRun(String status, String note) {
        openRun.setStatus(status); openRun.setNote(trim(note, 1024));
        openRun.setFinishedAt(LocalDateTime.now()); kiSyncRunRepository.save(openRun);
    }
    private void clearRun() { openRun = null; stagedSignals = null; stagedDocuments.clear(); ownerHash = null; }
    private static void validateSource(String uid, String label) {
        if (isBlank(uid) || uid.getBytes(StandardCharsets.UTF_8).length > 255 || !uid.equals(uid.trim())
                || uid.chars().anyMatch(Character::isISOControl) || !validUnicode(uid)
                || LEGACY_UID.equals(uid) || isBlank(label) || label.length() > 128
                || label.chars().anyMatch(Character::isISOControl) || !validUnicode(label))
            throw new KiScannerProtocolException("sourceUid/source ungueltig oder reservierter Legacy-Namensraum");
    }
    private static boolean validUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    private static String identity(String uid, long id) { return uid.length() + ":" + uid + ":" + id; }
    private static boolean sameOwner(KiDocumentEntity a, KiDocumentEntity b) {
        return Objects.equals(a.getSourceUid(), b.getSourceUid()) && Objects.equals(a.getSignalId(), b.getSignalId());
    }
    private static String encodeSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~").replace("*", "%2A");
    }
    private static KiDocumentEntity copyDocument(KiDocumentEntity original) {
        KiDocumentEntity copy = new KiDocumentEntity();
        org.springframework.beans.BeanUtils.copyProperties(original, copy);
        return copy;
    }
    /** Aggregate state for the dashboard tile and the /kiscanner page header. */
    public Map<String, Object> statusForDashboard() {
        Map<String, Object> status = new LinkedHashMap<>();
        KiSyncRunEntity attempt = kiSyncRunRepository.findTopByOrderByIdDesc().orElse(null);
        KiSyncRunEntity success = kiSyncRunRepository.findTopByStatusOrderByIdDesc("ok").orElse(null);
        KiSyncRunEntity last = success != null ? success : attempt;
        boolean hasData = success != null;
        status.put("lastAttemptStatus", attempt == null ? null : attempt.getStatus());
        status.put("lastAttemptRunId", attempt == null ? null : attempt.getId());
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

    public List<KiDocumentEntity> documentsForSignal(String sourceUid, long signalId) {
        return kiDocumentRepository.findBySourceUidAndSignalIdOrderByKindAscFileNameAsc(sourceUid, signalId);
    }

    public List<KiDocumentEntity> portfolioDocuments() {
        return kiDocumentRepository.findByGroupOrderByUpdatedAtDesc("portfolio");
    }

    public KiDocumentEntity document(long id) {
        return kiDocumentRepository.findById(id).orElse(null);
    }

    // --- Helpers -----------------------------------------------------------

    /** Protocol violation → HTTP 400 with a helpful message for the client. */
    public static class KiScannerProtocolException extends RuntimeException {
        public KiScannerProtocolException(String message) {
            super(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Preserve raw doubles; formatting belongs to the view. */
    private static Double finite(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }

    private static Double nonNegative(Double value) {
        return finite(value) != null && value >= 0 ? value : null;
    }

    private static Double positive(Double value) {
        return finite(value) != null && value > 0 ? value : null;
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
