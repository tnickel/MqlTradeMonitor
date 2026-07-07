package de.trademonitor.service;

import de.trademonitor.entity.MagicMappingEntity;
import de.trademonitor.repository.MagicMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MagicMappingService {

    @Autowired
    private MagicMappingRepository repository;

    /**
     * Get all mappings as a Map for easy lookup.
     */
    public Map<Long, String> getAllMappings() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(MagicMappingEntity::getMagicNumber, MagicMappingEntity::getCustomComment,
                        (existing, replacement) -> existing));
    }

    /**
     * Prefer magic-mapping name, fall back to trade comment from MT5.
     */
    public String resolveComment(long magicNumber, String tradeComment, Map<Long, String> mappings) {
        if (mappings != null) {
            String mapped = mappings.get(magicNumber);
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        }
        return tradeComment != null ? tradeComment : "";
    }

    public String resolveComment(long magicNumber, String tradeComment) {
        return resolveComment(magicNumber, tradeComment, getAllMappings());
    }

    /**
     * Save a mapping (create or update).
     */
    public void saveMapping(Long magicNumber, String comment) {
        if (comment == null)
            comment = "";
        MagicMappingEntity entity = new MagicMappingEntity(magicNumber, comment);
        repository.save(entity);
    }

    /**
     * Ensure mappings exist for all given magic numbers.
     * If a mapping is missing, it creates one using the default comment provider.
     */
    public void ensureMappingsExist(List<Long> magicNumbers, Function<Long, String> defaultCommentProvider) {
        // Load existing to avoid unnecessary writes
        Map<Long, String> existing = getAllMappings();

        for (Long magic : magicNumbers) {
            if (!existing.containsKey(magic)) {
                try {
                    if (!repository.existsById(magic)) {
                        String defaultComment = defaultCommentProvider.apply(magic);
                        saveMapping(magic, defaultComment != null ? defaultComment : "");
                    }
                } catch (Exception e) {
                    // Ignore duplicate key or concurrent write exceptions to prevent admin page load crashes
                    System.err.println("WARN: Could not save default magic mapping for " + magic + ": " + e.getMessage());
                }
            }
        }
    }
}
