package de.trademonitor.repository;

import de.trademonitor.entity.BannedIpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BannedIpRepository extends JpaRepository<BannedIpEntity, Long> {
    Optional<BannedIpEntity> findByIpAddress(String ipAddress);
}
