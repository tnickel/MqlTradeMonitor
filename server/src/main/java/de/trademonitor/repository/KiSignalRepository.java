package de.trademonitor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.trademonitor.entity.KiSignalEntity;

/**
 * Spring-Data repository for synced MqlKiScanner signal rows.
 */
public interface KiSignalRepository extends JpaRepository<KiSignalEntity, Long> {

    Optional<KiSignalEntity> findBySignalId(long signalId);

    List<KiSignalEntity> findAllByOrderByDisplayOrderAsc();
}
