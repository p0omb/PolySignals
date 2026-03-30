package ru.gbp.PolySignals.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.gbp.PolySignals.domain.entity.Whale;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WhaleRepository extends JpaRepository<Whale, Long> {

    Optional<Whale> findByUserId(String userId);

    @Query("SELECT w FROM Whale w ORDER BY w.volume DESC")
    List<Whale> findAllByVolumeDesc();

    @Query("SELECT w FROM Whale w ORDER BY w.profit DESC")
    List<Whale> findAllByProfitDesc();

    @Modifying
    @Query("UPDATE Whale w SET w.lastSeenAt = :now WHERE w.userId = :userId")
    int updateLastSeenAt(@Param("userId") String userId, @Param("now") Instant now);
}