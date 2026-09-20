package de.trademonitor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.trademonitor.entity.KiDocumentEntity;

/**
 * Spring-Data repository for documents transferred by the MqlKiScanner.
 */
public interface KiDocumentRepository extends JpaRepository<KiDocumentEntity, Long> {

    Optional<KiDocumentEntity> findByDocKey(String docKey);

    List<KiDocumentEntity> findBySignalIdOrderByKindAscFileNameAsc(Long signalId);

    List<KiDocumentEntity> findByGroupOrderByUpdatedAtDesc(String group);
}
