package de.trademonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.trademonitor.dto.TradeUpdateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Persists trade data to the file system.
 */
@Service
public class TradeStorage {

    @Value("${trade.storage.directory:./trades}")
    private String storageDirectory;

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Create storage directory
        File dir = new File(storageDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Created trade storage directory: " + dir.getAbsolutePath());
        }
    }

    /**
     * Save trade update to file system.
     */
    public void saveTrades(TradeUpdateRequest request) {
        try {
            // Create account directory
            File accountDir = new File(storageDirectory, String.valueOf(request.getAccountId()));
            if (!accountDir.exists()) {
                accountDir.mkdirs();
            }

            // Save to daily file
            String filename = LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".json";
            File file = new File(accountDir, filename);

            objectMapper.writeValue(file, request);
        } catch (IOException e) {
            System.err.println("Failed to save trades: " + e.getMessage());
        }
    }

    /**
     * Get storage directory path.
     */
    public String getStorageDirectory() {
        return storageDirectory;
    }
}
