package ru.gbp.PolySignals.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.gbp.PolySignals.domain.entity.Signal;

import java.time.Instant;
import java.util.List;

@Repository
public interface SignalRepository extends JpaRepository<Signal, Long> {

    boolean existsRecentSignal(
            @Param("marketId") String marketId,
            @Param("outcome") String outcome,
            @Param("from") Instant from
    );

    @Query("""
            SELECT s FROM Signal s 
            WHERE s.marketId = :marketId 
                AND s.outcome = :outcome 
            ORDER BY s.createdAt DESC
            """)
    List<Signal> findByMarketIdAndOutcomeOrderByCreatedAtDesc(
            @Param("marketId") String marketId,
            @Param("outcome") String outcome
    );

    @Query("""
        SELECT COUNT(s) FROM Signal s 
        WHERE s.createdAt >= :from
        """)
    long countSignalsSince(@Param("from") Instant from);

    // Cleanup старых сигналов (опционально)
    @Modifying
    @Query("DELETE FROM Signal s WHERE s.createdAt < :cutoff")
    int deleteOldSignals(@Param("cutoff") Instant cutoff);
}