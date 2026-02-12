package de.trademonitor.repository;

import de.trademonitor.entity.OpenTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpenTradeRepository extends JpaRepository<OpenTradeEntity, Long> {

    List<OpenTradeEntity> findByAccountId(long accountId);

    void deleteByAccountId(long accountId);

    long countByAccountId(long accountId);

    java.util.Optional<OpenTradeEntity> findFirstByMagicNumber(Long magicNumber);
}
