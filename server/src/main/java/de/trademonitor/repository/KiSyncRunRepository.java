package de.trademonitor.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.trademonitor.entity.KiSyncRunEntity;

/**
 * Spring-Data repository for MqlKiScanner sync runs.
 */
public interface KiSyncRunRepository extends JpaRepository<KiSyncRunEntity, Long> {

    Optional<KiSyncRunEntity> findTopByOrderByIdDesc();
}
