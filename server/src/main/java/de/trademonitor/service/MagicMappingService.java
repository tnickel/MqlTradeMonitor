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
                .collect(Collectors.toMap(MagicMappingEntity::getMagicNumber, MagicMappingEntity::getCustomComment));
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
                String defaultComment = defaultCommentProvider.apply(magic);
                saveMapping(magic, defaultComment != null ? defaultComment : "");
            }
        }
    }
}
