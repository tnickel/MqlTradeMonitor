package de.trademonitor.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request payload DTOs of the KiScanner sync protocol v1.
 *
 * The scanner (MqlKiScanner, Python/Streamlit) is not a MetaTrader EA
 * and therefore uses its own registration handshake and data format —
 * see Doku/MqlKiScanner_Integration.md. Unknown JSON fields are ignored
 * so newer scanners can add fields without breaking older servers.
 */
public final class KiScannerDtos {

    private KiScannerDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegisterRequest {
        private String client;
        private Integer protocolVersion;
        private String scannerVersion;
        private Integer signalCount;

        public String getClient() { return client; }
        public void setClient(String client) { this.client = client; }

        public Integer getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(Integer protocolVersion) { this.protocolVersion = protocolVersion; }

        public String getScannerVersion() { return scannerVersion; }
        public void setScannerVersion(String scannerVersion) { this.scannerVersion = scannerVersion; }

        public Integer getSignalCount() { return signalCount; }
        public void setSignalCount(Integer signalCount) { this.signalCount = signalCount; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignalRow {
        public Long signalId;
        public String name;
        public String platform;
        public String url;
        public String ampel;
        public Double score;
        public String urteil;
        public String kurzfassung;
        public String stop;
        public String stopEvidence;
        public Double tradingDdPct;
        public Double ddEquityPct;
        public Double ddBalancePct;
        public Double ertragMonatPct;
        public Double growthPct;
        public Double pf;
        public Double winratePct;
        public Double aboPreisUsd;
        public Double abonnenten;
        public Double wochen;
        public Integer aboDelta7;
        public Integer aboDelta30;
        public String aboStand;
        public Boolean martingale;
        public Integer peakPositionen;
        public Double peakNettoLots;
        public Double shockUsd;
        public Double kapitalbasisUsd;
        public String brokerServer;
        public String symbole;
        public String berichtVom;
        public Integer docsBerichte;
        public Integer docsTiefenanalyse;
        public Integer docsDownloader;
        public String tradesSha256;
        public String stand;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SignalsRequest {
        private List<SignalRow> signals;

        public List<SignalRow> getSignals() { return signals; }
        public void setSignals(List<SignalRow> signals) { this.signals = signals; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentRequest {
        private String docKey;
        private Long signalId;
        private String group;
        private String kind;
        private String label;
        private String fileName;
        private String contentType;
        private String sha256;
        private Long sizeBytes;
        private String lastModified;
        private String contentBase64;

        public String getDocKey() { return docKey; }
        public void setDocKey(String docKey) { this.docKey = docKey; }

        public Long getSignalId() { return signalId; }
        public void setSignalId(Long signalId) { this.signalId = signalId; }

        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }

        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

        public String getLastModified() { return lastModified; }
        public void setLastModified(String lastModified) { this.lastModified = lastModified; }

        public String getContentBase64() { return contentBase64; }
        public void setContentBase64(String contentBase64) { this.contentBase64 = contentBase64; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SyncStatsRequest {
        private Integer signals;
        private Integer documents;
        private Integer uploaded;
        private Integer skipped;
        private Long bytes;

        public Integer getSignals() { return signals; }
        public void setSignals(Integer signals) { this.signals = signals; }

        public Integer getDocuments() { return documents; }
        public void setDocuments(Integer documents) { this.documents = documents; }

        public Integer getUploaded() { return uploaded; }
        public void setUploaded(Integer uploaded) { this.uploaded = uploaded; }

        public Integer getSkipped() { return skipped; }
        public void setSkipped(Integer skipped) { this.skipped = skipped; }

        public Long getBytes() { return bytes; }
        public void setBytes(Long bytes) { this.bytes = bytes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AbortRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
