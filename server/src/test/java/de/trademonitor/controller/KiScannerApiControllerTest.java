package de.trademonitor.controller;

import java.util.Base64;
import java.util.Optional;

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
import de.trademonitor.repository.UserRepository;

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

    @MockBean
    private UserRepository userRepository;

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
}
