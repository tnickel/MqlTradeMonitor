package de.trademonitor.entity;

import jakarta.persistence.*;

/**
 * JPA entity: one MqlKiScanner signal row (synced snapshot).
 *
 * Written exclusively by the KiScanner sync protocol
 * (POST /api/kiscanner/signals). Each sync replaces the whole set:
 * rows missing from the latest snapshot are deleted.
 */
@Entity
@Table(name = "ki_signals")
public class KiSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Natural key: MQL5 signal id from the scanner. */
    @Column(name = "signal_id", nullable = false, unique = true)
    private long signalId;

    /** Display order as sent by the scanner (NEU rows first, then name). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "name", nullable = false, length = 255)
    private String name = "";

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "url", length = 512)
    private String url;

    /** Ampel emoji exactly as shown in the scanner table. */
    @Column(name = "ampel", length = 16)
    private String ampel;

    @Column(name = "score")
    private Double score;

    @Column(name = "urteil", length = 1024)
    private String urteil;

    @Column(name = "kurzfassung", columnDefinition = "CLOB")
    private String kurzfassung;

    @Column(name = "stop_nachweis", length = 512)
    private String stopNachweis;

    /** direct | cluster | partial | none | null (engine classification). */
    @Column(name = "stop_evidence", length = 32)
    private String stopEvidence;

    @Column(name = "trading_dd_pct")
    private Double tradingDdPct;

    @Column(name = "dd_equity_pct")
    private Double ddEquityPct;

    @Column(name = "dd_balance_pct")
    private Double ddBalancePct;

    @Column(name = "ertrag_monat_pct")
    private Double ertragMonatPct;

    @Column(name = "growth_pct")
    private Double growthPct;

    @Column(name = "pf")
    private Double pf;

    @Column(name = "winrate_pct")
    private Double winratePct;

    @Column(name = "abo_preis_usd")
    private Double aboPreisUsd;

    @Column(name = "abonnenten")
    private Double abonnenten;

    @Column(name = "wochen")
    private Double wochen;

    @Column(name = "abo_delta7")
    private Integer aboDelta7;

    @Column(name = "abo_delta30")
    private Integer aboDelta30;

    @Column(name = "abo_stand", length = 32)
    private String aboStand;

    @Column(name = "martingale")
    private Boolean martingale;

    @Column(name = "peak_positionen")
    private Integer peakPositionen;

    @Column(name = "peak_netto_lots")
    private Double peakNettoLots;

    @Column(name = "shock_usd")
    private Double shockUsd;

    @Column(name = "kapitalbasis_usd")
    private Double kapitalbasisUsd;

    @Column(name = "broker_server", length = 255)
    private String brokerServer;

    @Column(name = "symbole", length = 1024)
    private String symbole;

    /** Creation time of the scanner's Gesamtbericht (scanner timestamp). */
    @Column(name = "bericht_vom", length = 32)
    private String berichtVom;

    @Column(name = "docs_berichte")
    private Integer docsBerichte;

    @Column(name = "docs_tiefenanalyse")
    private Integer docsTiefenanalyse;

    @Column(name = "docs_downloader")
    private Integer docsDownloader;

    @Column(name = "trades_sha256", length = 64)
    private String tradesSha256;

    /** "NEU" marker from the scanner run, empty otherwise. */
    @Column(name = "stand", length = 16)
    private String stand;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    public KiSignalEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getSignalId() { return signalId; }
    public void setSignalId(long signalId) { this.signalId = signalId; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getAmpel() { return ampel; }
    public void setAmpel(String ampel) { this.ampel = ampel; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getUrteil() { return urteil; }
    public void setUrteil(String urteil) { this.urteil = urteil; }

    public String getKurzfassung() { return kurzfassung; }
    public void setKurzfassung(String kurzfassung) { this.kurzfassung = kurzfassung; }

    public String getStopNachweis() { return stopNachweis; }
    public void setStopNachweis(String stopNachweis) { this.stopNachweis = stopNachweis; }

    public String getStopEvidence() { return stopEvidence; }
    public void setStopEvidence(String stopEvidence) { this.stopEvidence = stopEvidence; }

    public Double getTradingDdPct() { return tradingDdPct; }
    public void setTradingDdPct(Double tradingDdPct) { this.tradingDdPct = tradingDdPct; }

    public Double getDdEquityPct() { return ddEquityPct; }
    public void setDdEquityPct(Double ddEquityPct) { this.ddEquityPct = ddEquityPct; }

    public Double getDdBalancePct() { return ddBalancePct; }
    public void setDdBalancePct(Double ddBalancePct) { this.ddBalancePct = ddBalancePct; }

    public Double getErtragMonatPct() { return ertragMonatPct; }
    public void setErtragMonatPct(Double ertragMonatPct) { this.ertragMonatPct = ertragMonatPct; }

    public Double getGrowthPct() { return growthPct; }
    public void setGrowthPct(Double growthPct) { this.growthPct = growthPct; }

    public Double getPf() { return pf; }
    public void setPf(Double pf) { this.pf = pf; }

    public Double getWinratePct() { return winratePct; }
    public void setWinratePct(Double winratePct) { this.winratePct = winratePct; }

    public Double getAboPreisUsd() { return aboPreisUsd; }
    public void setAboPreisUsd(Double aboPreisUsd) { this.aboPreisUsd = aboPreisUsd; }

    public Double getAbonnenten() { return abonnenten; }
    public void setAbonnenten(Double abonnenten) { this.abonnenten = abonnenten; }

    public Double getWochen() { return wochen; }
    public void setWochen(Double wochen) { this.wochen = wochen; }

    public Integer getAboDelta7() { return aboDelta7; }
    public void setAboDelta7(Integer aboDelta7) { this.aboDelta7 = aboDelta7; }

    public Integer getAboDelta30() { return aboDelta30; }
    public void setAboDelta30(Integer aboDelta30) { this.aboDelta30 = aboDelta30; }

    public String getAboStand() { return aboStand; }
    public void setAboStand(String aboStand) { this.aboStand = aboStand; }

    public Boolean getMartingale() { return martingale; }
    public void setMartingale(Boolean martingale) { this.martingale = martingale; }

    public Integer getPeakPositionen() { return peakPositionen; }
    public void setPeakPositionen(Integer peakPositionen) { this.peakPositionen = peakPositionen; }

    public Double getPeakNettoLots() { return peakNettoLots; }
    public void setPeakNettoLots(Double peakNettoLots) { this.peakNettoLots = peakNettoLots; }

    public Double getShockUsd() { return shockUsd; }
    public void setShockUsd(Double shockUsd) { this.shockUsd = shockUsd; }

    public Double getKapitalbasisUsd() { return kapitalbasisUsd; }
    public void setKapitalbasisUsd(Double kapitalbasisUsd) { this.kapitalbasisUsd = kapitalbasisUsd; }

    public String getBrokerServer() { return brokerServer; }
    public void setBrokerServer(String brokerServer) { this.brokerServer = brokerServer; }

    public String getSymbole() { return symbole; }
    public void setSymbole(String symbole) { this.symbole = symbole; }

    public String getBerichtVom() { return berichtVom; }
    public void setBerichtVom(String berichtVom) { this.berichtVom = berichtVom; }

    public Integer getDocsBerichte() { return docsBerichte; }
    public void setDocsBerichte(Integer docsBerichte) { this.docsBerichte = docsBerichte; }

    public Integer getDocsTiefenanalyse() { return docsTiefenanalyse; }
    public void setDocsTiefenanalyse(Integer docsTiefenanalyse) { this.docsTiefenanalyse = docsTiefenanalyse; }

    public Integer getDocsDownloader() { return docsDownloader; }
    public void setDocsDownloader(Integer docsDownloader) { this.docsDownloader = docsDownloader; }

    public String getTradesSha256() { return tradesSha256; }
    public void setTradesSha256(String tradesSha256) { this.tradesSha256 = tradesSha256; }

    public String getStand() { return stand; }
    public void setStand(String stand) { this.stand = stand; }

    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
