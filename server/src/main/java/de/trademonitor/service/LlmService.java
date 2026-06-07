package de.trademonitor.service;

import de.trademonitor.model.Account;
import de.trademonitor.model.Trade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Service
public class LlmService {

    private static final Logger LOG = Logger.getLogger(LlmService.class.getName());

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private GlobalConfigService globalConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .build();

    public String analyzeOpenTrades(long accountId) throws Exception {
        Account account = accountManager.getAccount(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account mit ID " + accountId + " nicht gefunden.");
        }

        String apiKey = globalConfigService.getOpenRouterApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Die Umgebungsvariable 'OPENROUTER_API_KEY' ist auf dem Server nicht konfiguriert.");
        }

        String model = globalConfigService.getLlmModel();
        String systemPrompt = globalConfigService.getLlmSystemPrompt();

        // 1. Construct user prompt containing custom strategy prompt and current trades
        StringBuilder userContent = new StringBuilder();
        userContent.append("Hier ist der spezifische Beurteilungs-Prompt für dieses Trading-Konto:\n");
        userContent.append(account.getCustomPrompt() != null && !account.getCustomPrompt().trim().isEmpty() 
                ? account.getCustomPrompt().trim() 
                : "Standard-Beurteilung: Prüfe, ob die offenen Positionen im normalen Bereich liegen.");
        userContent.append("\n\n");
        userContent.append("Hier sind die aktuellen Metriken des Kontos:\n");
        userContent.append("- Konto-ID: ").append(account.getAccountId()).append("\n");
        userContent.append("- Name: ").append(account.getName() != null ? account.getName() : "Unbenannt").append("\n");
        userContent.append("- Broker: ").append(account.getBroker()).append("\n");
        userContent.append("- Währung: ").append(account.getCurrency()).append("\n");
        userContent.append("- Kontostand (Balance): ").append(account.getBalance()).append(" ").append(account.getCurrency()).append("\n");
        userContent.append("- Eigenkapital (Equity): ").append(account.getEquity()).append(" ").append(account.getCurrency()).append("\n");
        userContent.append("- Aktueller offener P/L (Floating): ").append(account.getTotalProfit()).append(" ").append(account.getCurrency()).append("\n\n");

        userContent.append("Hier sind die aktuell offenen Positionen:\n");
        List<Trade> openTrades = account.getOpenTrades();
        if (openTrades.isEmpty()) {
            userContent.append("Keine offenen Positionen auf diesem Konto.\n");
        } else {
            for (Trade t : openTrades) {
                userContent.append("- Ticket: ").append(t.getTicket())
                        .append(", Symbol: ").append(t.getSymbol())
                        .append(", Typ: ").append(t.getType())
                        .append(", Lots: ").append(t.getVolume())
                        .append(", Einstiegspreis: ").append(t.getOpenPrice())
                        .append(", Profit/Loss: ").append(t.getProfit()).append(" ").append(account.getCurrency())
                        .append(", Swap: ").append(t.getSwap())
                        .append(", Magic-Nummer: ").append(t.getMagicNumber())
                        .append(", Eröffnungszeit: ").append(t.getOpenTime())
                        .append(", Kommentar: ").append(t.getComment() != null ? t.getComment() : "-")
                        .append("\n");
            }
        }

        userContent.append("\nBitte führe die Analyse basierend auf den obigen Informationen durch. ");
        userContent.append("Beurteile, ob das Risiko im normalen Rahmen liegt oder ob Handlungsbedarf besteht. ");
        userContent.append("Verfasse deine Antwort direkt auf Deutsch. Strukturiere die Antwort übersichtlich in Markdown (Absätze, Listen, evtl. fette Hervorhebungen). ");
        userContent.append("Beginne die Antwort mit einer eindeutigen Risikoampel: z. B. '🟢 ALLES OK', '⚠️ ERHÖHTES RISIKO / BEOBACHTEN' oder '🔴 KRITISCHER ALARM'.");

        // 2. Build JSON Request payload using Jackson ObjectMapper
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        
        ArrayNode messages = requestBody.putArray("messages");
        
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userContent.toString());

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        LOG.info("Sending OpenRouter request using model: " + model + " for account " + accountId);

        // 3. Prepare HTTP Request to OpenRouter API
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("HTTP-Referer", "https://monitor.tnickel-ki.de")
                .header("X-Title", "MqlTradeMonitor")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // 4. Send request synchronously
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errDetail = "HTTP Status " + response.statusCode() + ": " + response.body();
            LOG.severe("OpenRouter API error: " + errDetail);
            throw new RuntimeException("OpenRouter API lieferte einen Fehler: " + errDetail);
        }

        // 5. Parse result
        String body = response.body();
        ObjectNode json = (ObjectNode) objectMapper.readTree(body);
        String textResult = json.at("/choices/0/message/content").asText();

        if (textResult == null || textResult.trim().isEmpty()) {
            throw new RuntimeException("Die KI-Antwort war leer. Raw response: " + body);
        }

        // 6. Save results to account (in-memory + database)
        LocalDateTime now = LocalDateTime.now();
        accountManager.updatePromptAnalysisResult(accountId, textResult, now);

        LOG.info("LLM Trade Analysis completed for account " + accountId);
        return textResult;
    }
}
