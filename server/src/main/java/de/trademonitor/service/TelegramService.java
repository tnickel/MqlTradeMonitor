package de.trademonitor.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class TelegramService {

    @Autowired
    private GlobalConfigService configService;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendNotification(String text) {
        if (!configService.isTelegramEnabled()) {
            return;
        }
        String token = configService.getTelegramBotToken();
        String chatId = configService.getTelegramChatId();

        if (token == null || token.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
            System.err.println("Telegram Bot is enabled but Token or ChatId is not configured.");
            return;
        }

        sendRawTelegramMessage(token, chatId, text);
    }

    public boolean sendRawTelegramMessage(String token, String chatId, String text) {
        CompletableFuture.runAsync(() -> sendRawTelegramMessageSync(token, chatId, text));
        return true;
    }

    public boolean sendRawTelegramMessageSync(String token, String chatId, String text) {
        try {
            String url = "https://api.telegram.org/bot" + token + "/sendMessage";

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "Markdown");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(url, request, String.class);
            return true;
        } catch (Exception e) {
            System.err.println("Error sending Telegram message: " + e.getMessage());
            return false;
        }
    }
}
