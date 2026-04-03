package ru.gbp.PolySignals.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.gbp.PolySignals.domain.entity.Bet;

import java.time.Instant;
import java.util.List;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {

    boolean existsByUniqueKey(String uniqueKey);

    @Query(value = """
            SELECT 
                b.market_id as marketId,
                b.outcome as outcome,
                COUNT(DISTINCT b.whale_id) as whalesCount,
                SUM(b.amount) as totalAmount
            FROM bet b 
            WHERE b.transaction_time >= :from 
                AND b.transaction_time <= :to
            GROUP BY b.market_id, b.outcome
            HAVING COUNT(DISTINCT b.whale_id) >= :minWhales
            """,
            nativeQuery = true)
    List<SignalCandidateProjection> findSignalCandidates(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("minWhales") int minWhales
    );

    @Query("""
            SELECT
                b.marketId as marketId,
                b.outcome as outcome,
                COUNT(DISTINCT b.whale.id) as whalesCount,
                SUM(b.amount) as totalAmount,
                MIN(b.transactionTime) as firstBetAt,
                MAX(b.transactionTime) as lastBetAt
            FROM Bet b
            WHERE b.marketId = :marketId
              AND b.outcome = :outcome
              AND b.transactionTime >= :from
              AND b.transactionTime <= :to
            GROUP BY b.marketId, b.outcome
            """)
    MarketOutcomeSignalProjection findMarketOutcomeSignalStats(
            @Param("marketId") String marketId,
            @Param("outcome") String outcome,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

//    @Query("""
//            SELECT COUNT(s) > 0
//            FROM Signal s
//            WHERE s.marketId = :marketId
//                AND s.outcome = :outcome
//                AND s.createdAt >= :from
//            """)
//    boolean existsRecentSignal(
//            @Param("marketId") String marketId,
//            @Param("outcome") String outcome,
//            @Param("from") Instant from
//    );

    // Дополнительно: для отладки
    @Query("SELECT b FROM Bet b WHERE b.marketId = :marketId AND b.outcome = :outcome ORDER BY b.transactionTime DESC")
    List<Bet> findRecentBetsByMarketAndOutcome(
            @Param("marketId") String marketId,
            @Param("outcome") String outcome
    );
}
