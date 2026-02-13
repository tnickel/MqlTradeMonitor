package de.trademonitor.repository;

import de.trademonitor.entity.MagicMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MagicMappingRepository extends JpaRepository<MagicMappingEntity, Long> {
}
