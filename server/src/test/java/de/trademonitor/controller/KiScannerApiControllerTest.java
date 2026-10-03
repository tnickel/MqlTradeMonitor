package de.trademonitor.controller;

import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import de.trademonitor.entity.UserEntity;
import de.trademonitor.entity.KiSignalEntity;
import de.trademonitor.repository.KiSignalRepository;
import de.trademonitor.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the KiScanner sync protocol v1: key auth, handshake validation,
 * the register → signals → documents → complete flow and the protected
 * browser endpoints (tile status, PDF view, /kiscanner page).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class KiScannerApiControllerTest {

    private static final String API_KEY = "test-scanner-key";
    private static final String PDF_BASE64 = Base64.getEncoder()
            .encodeToString("%PDF-1.4 testbericht".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KiSignalRepository kiSignalRepository;

    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    public void clearSignalSnapshot() {
        kiSignalRepository.deleteAllInBatch();
    }

    private void keyValid() {
        UserEntity user = new UserEntity("scanner-user", "ignored", "ROLE_USER");
        user.setApiKey(API_KEY);
        // Reihenfolge wichtig: der spezifische Stub muss NACH anyString kommen,
        // sonst gewinnt anyString (letzter passender Stub gewinnt in Mockito).
        when(userRepository.findByApiKey(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByApiKey(API_KEY)).thenReturn(Optional.of(user));
    }

    @Test
    public void pingWithoutKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/kiscanner/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    public void pingWithValidKeyReportsProtocol() throws Exception {
        keyValid();
        mockMvc.perform(get("/api/kiscanner/ping").header("X-User-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.kiscannerApi").value("v1"));
    }

    @Test
    public void registerRejectsWrongClientName() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/register").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\":\"SomeEA\",\"protocolVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    public void registerRejectsWrongProtocolVersion() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/register").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\":\"MqlKiScanner\",\"protocolVersion\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    public void registerRejectsMissingKey() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\":\"MqlKiScanner\",\"protocolVersion\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void fullSyncFlowStoresSignalsDocumentsAndCompletes() throws Exception {
        keyValid();

        MvcResult register = mockMvc.perform(post("/api/kiscanner/register")
                        .header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\":\"MqlKiScanner\",\"protocolVersion\":1,"
                                + "\"scannerVersion\":\"0.1.0\",\"signalCount\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNumber())
                .andExpect(jsonPath("$.documents").isArray())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                register.getResponse().getContentAsString().contains("serverTime"));

        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signals\":["
                                + "{\"signalId\":2349227,\"name\":\"Gold Spike\",\"platform\":\"MT5\","
                                + "\"ampel\":\"🟢\",\"score\":3.2,\"urteil\":\"Kandidat\","
                                + "\"stop\":\"bewiesen (MT4-Orderbuch)\",\"tradingDdPct\":8.1,"
                                + "\"ddEquityPct\":3.8,\"ertragMonatPct\":6.2,\"docsBerichte\":3,"
                                + "\"docsTiefenanalyse\":1,\"stand\":\"NEU\"},"
                                + "{\"signalId\":2342895,\"name\":\"KiraCat\",\"ampel\":\"🟡\",\"score\":5.0}"
                                + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored").value(2))
                .andExpect(jsonPath("$.deleted").value(0));

        mockMvc.perform(post("/api/kiscanner/documents").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docKey\":\"signal/2349227/03-gesamtbericht.pdf\","
                                + "\"signalId\":2349227,\"group\":\"eigene\","
                                + "\"kind\":\"gesamtbericht\",\"label\":\"3 · Gesamtbericht\","
                                + "\"fileName\":\"03-gesamtbericht.pdf\","
                                + "\"contentType\":\"application/pdf\","
                                + "\"sizeBytes\":20,\"contentBase64\":\"" + PDF_BASE64 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored").value(true));

        mockMvc.perform(post("/api/kiscanner/complete").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signals\":2,\"documents\":1,\"uploaded\":1,"
                                + "\"skipped\":0,\"bytes\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").isNumber());

        // Tile status reflects the completed run.
        mockMvc.perform(get("/api/kiscanner/status").with(user("dashboard").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.signals").value(2))
                .andExpect(jsonPath("$.documents").value(1))
                .andExpect(jsonPath("$.lastRunAtText").isNotEmpty());
    }

    @Test
    public void signalsSnapshotReplacesPreviousRows() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/register").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\":\"MqlKiScanner\",\"protocolVersion\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signals\":[{\"signalId\":1,\"name\":\"A\"},"
                                + "{\"signalId\":2,\"name\":\"B\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored").value(2));
        // Second snapshot without signal 2 → mirror semantics remove it.
        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signals\":[{\"signalId\":1,\"name\":\"A2\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored").value(1))
                .andExpect(jsonPath("$.deleted").value(1));
        mockMvc.perform(post("/api/kiscanner/abort").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void documentRejectsNonPdfContentType() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/documents").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docKey\":\"x/1\",\"fileName\":\"x.txt\","
                                + "\"contentType\":\"text/plain\","
                                + "\"contentBase64\":\"aGVsbG8=\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void documentRejectsWrongMagicBytes() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/documents").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docKey\":\"x/2\",\"fileName\":\"fake.pdf\","
                                + "\"contentType\":\"application/pdf\","
                                + "\"contentBase64\":\"aGVsbG8gd29ybGQ=\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void browserEndpointsRequireSession() throws Exception {
        mockMvc.perform(get("/api/kiscanner/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/kiscanner/documents/1/view"))
                .andExpect(status().isUnauthorized());
        // The page itself redirects anonymous browsers to the login page.
        mockMvc.perform(get("/kiscanner"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    public void kiscannerPageRendersForLoggedInUsers() throws Exception {
        keyValid();
        // Seite ohne Daten rendert die Leerstands-Nachricht.
        mockMvc.perform(get("/kiscanner").with(user("dashboard").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("MqlKiScanner")));
    }

    @Test
    public void kiscannerPageRendersSortableTableWithSignalData() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/register")
                        .header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client\":\"MqlKiScanner\",\"protocolVersion\":1,\"signalCount\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signals\":[{\"signalId\":2349227,\"name\":\"Gold Spike\",\"ampel\":\"🟢\","
                                + "\"score\":3.2,\"tradingDdPct\":8.1,\"ertragMonatPct\":6.2,\"abonnenten\":120}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/kiscanner").with(user("dashboard").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("th class=\"sortable\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("data-val=\"3.2\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("initKiTableSorting")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("headerTooltip")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("initHeaderTooltips")));
    }

    @Test
    public void equityEfficiencySyncPreservesRawPrecisionAndDisplaysOwnMeasurements() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signals":[{"signalId":900009,"name":"Precision","ampel":"🟡",
                                "tradingDdPct":0.1,"ddEquityPct":34.95,"ertragMonatPct":99,
                                "maxDrawdownEquityPct":6,"ertragMonatGeomPct":5.99994,
                                "retddMonat":0.9999899999999999,"cagrJahrPct":72.123456,
                                "retddJahr":12.020576,"retddBasis":"gemessener_max_equity_drawdown_inkl_floating",
                                "drawdownLimitPct":20,"minReturnMonthlyPct":7,"minRetddMonthly":1}]}
                                """))
                .andExpect(status().isOk());
        KiSignalEntity stored = kiSignalRepository.findBySignalId(900009L).orElseThrow();
        assertEquals(6.0, stored.getMaxDrawdownEquityPct());
        assertEquals(5.99994, stored.getErtragMonatGeomPct());
        assertEquals(0.9999899999999999, stored.getRetddMonat());
        assertTrue(stored.getEquityRetddMonat() < 1);
        assertEquals(72.123456, stored.getCagrJahrPct());
        assertEquals(12.020576, stored.getRetddJahr());
        assertEquals(20.0, stored.getDrawdownLimitPct());
        assertEquals(7.0, stored.getMinReturnMonthlyPct());
        assertEquals(1.0, stored.getMinRetddMonthly());
        assertEquals("gemessener_max_equity_drawdown_inkl_floating", stored.getRetddBasis());
        assertEquals("🟡", stored.getAmpel());

        String html = mockMvc.perform(get("/kiscanner").with(user("dashboard").roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("data-dd=\"6.0\""));
        assertTrue(html.contains("data-ertrag=\"5.99994\""));
        assertTrue(html.contains("data-retdd=\"0.9999899999999999\""));
        assertTrue(html.contains("equity-dd-cell dd-green"));
        // Display rounding must not turn a ratio below the raw threshold green.
        assertTrue(html.contains("font-weight:700;color:#eab308"));
        assertTrue(html.contains("ki-details-900009"));
        assertTrue(html.contains("Gewinn %/Monat"));
        assertTrue(html.contains("data-dd-limit=\"20.0\""));
        assertTrue(html.contains("data-min-return=\"7.0\""));
    }

    @Test
    public void equityDrawdownColorsUseTransmittedLimitAndKeepMissingNeutral() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signals":[
                                {"signalId":1,"name":"Green","maxDrawdownEquityPct":16,"drawdownLimitPct":20},
                                {"signalId":2,"name":"Yellow","maxDrawdownEquityPct":20,"drawdownLimitPct":20},
                                {"signalId":3,"name":"Red","maxDrawdownEquityPct":20.00001,"drawdownLimitPct":20},
                                {"signalId":4,"name":"Missing measurement","ddEquityPct":1,"drawdownLimitPct":20},
                                {"signalId":5,"name":"Missing threshold","maxDrawdownEquityPct":1}]}
                                """))
                .andExpect(status().isOk());
        String html = mockMvc.perform(get("/kiscanner").with(user("dashboard").roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquityColor(html, "16.0", "green");
        assertEquityColor(html, "20.0", "yellow");
        assertEquityColor(html, "20.00001", "red");
        assertEquityColor(html, "1.0", "unknown");
        assertEquals(20.00001, kiSignalRepository.findBySignalId(3L).orElseThrow().getMaxDrawdownEquityPct());
        assertNull(kiSignalRepository.findBySignalId(4L).orElseThrow().getMaxDrawdownEquityPct());
        assertNull(kiSignalRepository.findBySignalId(5L).orElseThrow().getDrawdownLimitPct());
    }

    @Test
    public void legacyAndMissingPayloadsHaveNoEquityEfficiencyFallback() throws Exception {
        keyValid();
        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signals":[
                                {"signalId":10,"name":"Legacy","tradingDdPct":0.1,"ddEquityPct":8,
                                 "ertragMonatPct":99,"ertragMonatGeomPct":12,"retddMonat":120,"retddJahr":1200},
                                {"signalId":11,"name":"Zero EQ","maxDrawdownEquityPct":0,
                                 "ertragMonatGeomPct":12,"retddMonat":120,"retddBasis":"gemessener_max_equity_drawdown_inkl_floating"},
                                {"signalId":12,"name":"Missing gain","maxDrawdownEquityPct":6,
                                 "ertragMonatPct":99,"retddMonat":120,"retddBasis":"gemessener_max_equity_drawdown_inkl_floating"}]}
                                """))
                .andExpect(status().isOk());
        for (long id : new long[] {10, 11, 12}) {
            KiSignalEntity stored = kiSignalRepository.findBySignalId(id).orElseThrow();
            assertNull(stored.getEquityRetddMonat());
            assertNull(stored.getEquityRetddJahr());
        }
        assertNull(kiSignalRepository.findBySignalId(12L).orElseThrow().getErtragMonatGeomPct());
        mockMvc.perform(get("/kiscanner").with(user("dashboard").roles("USER")))
                .andExpect(status().isOk());
        // Snapshot semantics also clear previously present fields rather than retaining a stale ratio.
        mockMvc.perform(post("/api/kiscanner/signals").header("X-User-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signals\":[{\"signalId\":11,\"name\":\"Unknown\"}]}"))
                .andExpect(status().isOk());
        KiSignalEntity cleared = kiSignalRepository.findBySignalId(11L).orElseThrow();
        assertNull(cleared.getMaxDrawdownEquityPct());
        assertNull(cleared.getErtragMonatGeomPct());
        assertNull(cleared.getRetddMonat());
        assertNull(cleared.getRetddBasis());
    }

    private static void assertEquityColor(String html, String rawValue, String color) {
        String cell = "<td\\b(?=[^>]*class=\"num equity-dd-cell dd-" + color
                + "\")(?=[^>]*data-val=\"" + Pattern.quote(rawValue) + "\")[^>]*>";
        assertTrue(Pattern.compile(cell).matcher(html).find(), rawValue + " should be " + color);
    }
}
