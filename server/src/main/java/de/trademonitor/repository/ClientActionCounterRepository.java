package de.trademonitor.repository;

import de.trademonitor.entity.ClientActionCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientActionCounterRepository extends JpaRepository<ClientActionCounter, Long> {

    Optional<ClientActionCounter> findByAccountIdAndActionAndDate(Long accountId, String action, LocalDate date);

    List<ClientActionCounter> findByDateOrderByAccountIdAsc(LocalDate date);

    List<ClientActionCounter> findByAccountIdAndDateBetweenOrderByDateAsc(Long accountId, LocalDate from, LocalDate to);

    List<ClientActionCounter> findByDateBetweenOrderByAccountIdAscDateAsc(LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT c.accountId FROM ClientActionCounter c ORDER BY c.accountId ASC")
    List<Long> findDistinctAccountIds();

    @Query("SELECT SUM(c.count) FROM ClientActionCounter c WHERE c.accountId = :accountId AND c.action = :action AND c.date BETWEEN :from AND :to")
    Long sumCountByAccountIdAndActionAndDateBetween(Long accountId, String action, LocalDate from, LocalDate to);

    @Transactional
    @Modifying
    @Query("DELETE FROM ClientActionCounter c WHERE c.date < :cutoff")
    void deleteByDateBefore(LocalDate cutoff);
}
