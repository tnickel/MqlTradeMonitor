package de.trademonitor.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.trademonitor.entity.*;
import de.trademonitor.repository.*;
import de.trademonitor.service.KiScannerService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class KiScannerV2Test {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired KiSignalRepository signals;
    @Autowired KiDocumentRepository documents;
    @Autowired KiScannerService service;
    @Autowired JdbcTemplate jdbc;
    @MockBean UserRepository users;
    private static final String KEY = "v2-test-key";
    private static final String PDF = Base64.getEncoder().encodeToString("%PDF-1.4 owner".getBytes());
    private static final String UID_A = "broker / alpha";
    private static final String KEY_A = "signal/broker%20%2F%20alpha/7/report.pdf";

    @BeforeEach void reset() {
        ReflectionTestUtils.setField(service, "openRun", null);
        signals.deleteAllInBatch(); documents.deleteAllInBatch();
        jdbc.update("UPDATE ki_scanner_protocol_state SET v2_active=FALSE WHERE id=1");
        UserEntity user = new UserEntity("scanner", "ignored", "ROLE_USER"); user.setApiKey(KEY);
        when(users.findByApiKey(anyString())).thenReturn(Optional.empty());
        when(users.findByApiKey(KEY)).thenReturn(Optional.of(user));
    }

    private long register(int count) throws Exception {
        String json = postJson("register", Map.of("client", "MqlKiScanner", "protocolVersion", 2, "signalCount", count), 200);
        return mapper.readTree(json).get("runId").asLong();
    }
    private String postJson(String path, Object data, int code) throws Exception {
        return mvc.perform(post("/api/kiscanner/" + path).header("X-User-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(data)))
                .andExpect(status().is(code)).andReturn().getResponse().getContentAsString();
    }
    private Map<String, Object> row(String uid, String source, String name) {
        return Map.of("sourceUid", uid, "source", source, "signalId", 7, "name", name);
    }
    private void snapshot(long run, Object rows) throws Exception {
        postJson("signals", Map.of("runId", run, "signals", rows), 200);
    }
    private Map<String, Object> doc(long run, String uid, String source, String key) {
        Map<String, Object> doc = new HashMap<>(Map.of("runId", run, "sourceUid", uid,
                "source", source, "signalId", 7, "docKey", key, "fileName", "report.pdf",
                "group", "eigene", "contentBase64", PDF));
        return doc;
    }
    private void complete(long run, int count, List<String> keys, int uploaded) throws Exception {
        postJson("complete", Map.of("runId", run, "signals", count, "documents", keys.size(),
                "uploaded", uploaded, "skipped", keys.size() - uploaded,
                "bytes", uploaded * Base64.getDecoder().decode(PDF).length, "documentKeys", keys), 200);
    }

    @Test void sameProviderIdAndDocumentsStaySeparateAndCommitAtomically() throws Exception {
        long run = register(2);
        snapshot(run, List.of(row(UID_A, "alpha", "Alpha"), row("beta", "beta", "Beta")));
        assertEquals(0, signals.count());
        postJson("documents", doc(run, UID_A, "alpha", KEY_A), 200);
        postJson("documents", doc(run, "beta", "beta", "signal/beta/7/report.pdf"), 200);
        assertEquals(0, documents.count());
        complete(run, 2, List.of(KEY_A, "signal/beta/7/report.pdf"), 2);
        assertEquals(2, signals.count()); assertEquals(2, documents.count());
        assertEquals(1, service.documentsForSignal(UID_A, 7).size());
        assertEquals(1, service.documentsForSignal("beta", 7).size());
        String html = mvc.perform(get("/kiscanner").with(user("browser").roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (KiSignalEntity signal : signals.findAll()) assertTrue(html.contains("ki-details-" + signal.getId()));
        assertTrue(html.contains("Alpha")); assertTrue(html.contains("Beta"));
    }

    @Test void sourceRenameRetainsIdentityAndCachedPdfOwner() throws Exception {
        long first = register(1); snapshot(first, List.of(row(UID_A, "old", "Before")));
        postJson("documents", doc(first, UID_A, "old", KEY_A), 200);
        complete(first, 1, List.of(KEY_A), 1);
        long internalId = signals.findAll().get(0).getId();
        long second = register(1); snapshot(second, List.of(row(UID_A, "renamed", "After")));
        complete(second, 1, List.of(KEY_A), 0);
        assertEquals(internalId, signals.findAll().get(0).getId());
        assertEquals("renamed", documents.findAll().get(0).getSource());
        assertEquals(UID_A, documents.findAll().get(0).getSourceUid());
    }

    @Test void abortAndInvalidCompletionPreservePreviousCompleteSnapshot() throws Exception {
        long first = register(1); snapshot(first, List.of(row("old", "old", "Committed")));
        complete(first, 1, List.of(), 0);
        long next = register(1); snapshot(next, List.of(row("next", "next", "Staged")));
        postJson("complete", Map.of("runId", next, "signals", 1, "documents", 1, "uploaded", 0,
                "skipped", 1, "bytes", 0, "documentKeys", List.of("missing")), 400);
        assertEquals("Committed", signals.findAll().get(0).getName());
        postJson("abort", Map.of("runId", next, "reason", "test"), 200);
        assertEquals("Committed", signals.findAll().get(0).getName());
        mvc.perform(get("/api/kiscanner/status").with(user("browser").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.lastAttemptStatus").value("aborted"));
        postJson("signals", Map.of("runId", next, "signals", List.of()), 400);
    }

    @Test void duplicateIdentityWrongRunAndCrossOwnerPdfAreRejected() throws Exception {
        long run = register(1);
        postJson("signals", Map.of("runId", run, "signals", List.of(row("a", "a", "A"), row("a", "a", "B"))), 400);
        postJson("signals", Map.of("runId", run + 1, "signals", List.of()), 400);
        postJson("signals", Map.of("signals", List.of()), 400);
        snapshot(run, List.of(row(UID_A, "a", "A")));
        postJson("documents", doc(run, "foreign", "foreign", KEY_A), 400);
        postJson("documents", doc(run, UID_A, "a", "signal/7/report.pdf"), 400);
        Map<String, Object> hashLie = doc(run, UID_A, "a", KEY_A); hashLie.put("sha256", "0".repeat(64));
        postJson("documents", hashLie, 400);
        UserEntity other = new UserEntity("other", "ignored", "ROLE_USER"); other.setApiKey("other-key");
        when(users.findByApiKey("other-key")).thenReturn(Optional.of(other));
        mvc.perform(post("/api/kiscanner/abort").header("X-User-Key", "other-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"runId\":" + run + "}"))
                .andExpect(status().isBadRequest());
        postJson("abort", Map.of("runId", run), 200);
    }

    @Test void firstV2CommitRetiresLegacyAndBlocksLaterV1Writes() throws Exception {
        postJson("register", Map.of("client", "MqlKiScanner", "protocolVersion", 1), 200);
        postJson("signals", Map.of("signals", List.of(Map.of("signalId", 7, "name", "Legacy"))), 200);
        assertEquals("legacy:v1", signals.findAll().get(0).getSourceUid());
        long run = register(1); snapshot(run, List.of(row("new", "new", "New")));
        assertEquals("Legacy", signals.findAll().get(0).getName());
        complete(run, 1, List.of(), 0);
        assertEquals(1, signals.count()); assertEquals("new", signals.findAll().get(0).getSourceUid());
        postJson("register", Map.of("client", "MqlKiScanner", "protocolVersion", 1), 400);
        postJson("signals", Map.of("signals", List.of()), 400);
    }

    @Test void completionRejectsCountsAndRemovesAbsentDocumentsOnlyAtCommit() throws Exception {
        long run = register(1); snapshot(run, List.of(row(UID_A, "alpha", "A")));
        postJson("documents", doc(run, UID_A, "alpha", KEY_A), 200);
        postJson("complete", Map.of("runId", run, "signals", 2, "documents", 1,
                "uploaded", 1, "skipped", 0, "bytes", 13, "documentKeys", List.of(KEY_A)), 400);
        assertEquals(0, signals.count()); complete(run, 1, List.of(KEY_A), 1);
        long second = register(1); snapshot(second, List.of(row(UID_A, "alpha", "A")));
        assertEquals(1, documents.count()); complete(second, 1, List.of(), 0);
        assertEquals(0, documents.count());
    }

    @Test void failedDatabaseCommitRollsBackSignalReplacementAndCanAbort() throws Exception {
        long first = register(1); snapshot(first, List.of(row("old", "old", "Committed")));
        complete(first, 1, List.of(), 0);
        long next = register(1); snapshot(next, List.of(row("new", "new", "x".repeat(300))));
        assertThrows(Exception.class, () -> complete(next, 1, List.of(), 0));
        assertEquals(1, signals.count()); assertEquals("Committed", signals.findAll().get(0).getName());
        postJson("abort", Map.of("runId", next), 200);
        assertEquals("Committed", signals.findAll().get(0).getName());
    }

    @Test void writesRequireHandshakeAndRfc3986UidEncodingMatchesPythonQuote() throws Exception {
        postJson("signals", Map.of("signals", List.of()), 400);
        long run = register(1); snapshot(run, List.of(row("star*~", "star", "Star")));
        postJson("documents", doc(run, "star*~", "star", "signal/star*~/7/report.pdf"), 400);
        postJson("documents", doc(run, "star*~", "star", "signal/star%2A~/7/report.pdf"), 200);
        complete(run, 1, List.of("signal/star%2A~/7/report.pdf"), 1);
    }

    private Map<String, Object> annual(String uid, double ratio) {
        Map<String, Object> data = new HashMap<>(row(uid, uid, uid));
        data.put("retddJahr", ratio); data.put("cagrJahrPct", ratio * 10);
        data.put("maxDrawdownEquityPct", 10); data.put("retddMonat", 0.1);
        data.put("ertragMonatGeomPct", 1); data.put("drawdownLimitPct", 50);
        data.put("retddBasis", "gemessener_max_equity_drawdown_inkl_floating");
        data.put("minCalmarJahr", 3); data.put("equityMeasurementStatus", "Gemessen: H1-Equity inklusive Floating");
        return data;
    }
    private String page() throws Exception {
        return mvc.perform(get("/kiscanner").with(user("browser").roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private void annualColor(String html, String raw, String color) {
        assertTrue(java.util.regex.Pattern.compile(
                "<td class=\"num retdd-cell\"[^>]*data-val=\"" + java.util.regex.Pattern.quote(raw)
                + "\"[^>]*>\\s*<span[^>]*color:" + color).matcher(html).find(), html);
    }

    @Test void annualCalmarUsesAnnualThresholdRawPrecisionAndTransmittedFiftyPercentLimit() throws Exception {
        long run = register(2);
        Map<String, Object> below = annual("below", 2.9999999);
        below.put("maxDrawdownEquityPct", 50);
        snapshot(run, List.of(below, annual("above", 4)));
        complete(run, 2, List.of(), 0);
        String html = page();
        annualColor(html, "2.9999999", "#eab308"); annualColor(html, "4.0", "#22c55e");
        assertTrue(html.contains("Calmar/Jahr")); assertTrue(html.contains("data-min-calmar=\"3.0\""));
        assertTrue(html.contains("data-dd-limit=\"50.0\""));
        assertTrue(java.util.regex.Pattern.compile("<td[^>]*class=\"num equity-dd-cell dd-yellow\"[^>]*data-val=\"50.0\"")
                .matcher(html).find());
        assertEquals(2.9999999, signals.findBySourceUidAndSignalId("below", 7).orElseThrow().getEquityRetddJahr());
    }

    @Test void provisionalAnnualValueIsOrangeAndNeverQualifiedDespiteApparentlyGoodRatio() throws Exception {
        long run = register(1);
        Map<String, Object> data = annual("provisional", 100);
        data.put("retddJahrVorbehalt", 200); data.put("retddVorbehaltGrund", "Kursabdeckung unvollstaendig");
        data.put("equityMeasurementStatus", "Keine belastbare Equity-Messung: Luecke");
        snapshot(run, List.of(data)); complete(run, 1, List.of(), 0);
        KiSignalEntity stored = signals.findAll().get(0);
        assertNull(stored.getEquityRetddJahr()); assertEquals(200.0, stored.getDisplayRetddJahr());
        String html = page(); annualColor(html, "200.0", "#f97316");
        assertTrue(html.contains("≈ 200")); assertTrue(html.contains("Kursabdeckung unvollstaendig"));
    }

    @Test void absentHistoricalAnnualThresholdRemainsUnknownAndSnapshotClearsPriorThreshold() throws Exception {
        long run = register(1); snapshot(run, List.of(annual("same", 5))); complete(run, 1, List.of(), 0);
        long next = register(1); Map<String, Object> data = annual("same", 5); data.remove("minCalmarJahr");
        snapshot(next, List.of(data)); complete(next, 1, List.of(), 0);
        assertNull(signals.findAll().get(0).getMinCalmarJahr());
        String html = page(); annualColor(html, "5.0", "#9ca3af"); assertTrue(html.contains("unbekannt"));
    }
}
