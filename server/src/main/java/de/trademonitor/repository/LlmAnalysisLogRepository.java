package de.trademonitor.repository;

import de.trademonitor.entity.LlmAnalysisLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmAnalysisLogRepository extends JpaRepository<LlmAnalysisLogEntity, Long> {
    List<LlmAnalysisLogEntity> findByAccountIdOrderByTimestampDesc(long accountId);

    @org.springframework.transaction.annotation.Transactional
    void deleteByAccountId(long accountId);
}
