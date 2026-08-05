package com.bettingcopilot.api.repository;

import com.bettingcopilot.api.entity.Recommendation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

    @Query(
            value =
                    """
        SELECT DISTINCT ON (r.game_id, r.market, r.side) r.*
        FROM recommendations r
        WHERE r.slate_run_id = :slateRunId
        ORDER BY r.game_id, r.market, r.side, r.created_at DESC
        """,
            nativeQuery = true)
    List<Recommendation> findDeduplicatedBySlateRunId(@Param("slateRunId") UUID slateRunId);

    Optional<Recommendation> findTopByGameIdOrderByCreatedAtDesc(String gameId);

    Optional<Recommendation> findTopByGameIdAndDecisionOrderByCreatedAtDesc(
            String gameId, String decision);

    @Query(
            value =
                    """
        SELECT DISTINCT ON (r.game_id, r.market, r.side) r.*
        FROM recommendations r
        JOIN slate_runs sr ON r.slate_run_id = sr.slate_run_id
        WHERE r.decision = 'medium'
          AND sr.run_date BETWEEN :startDate AND :endDate
        ORDER BY r.game_id, r.market, r.side, r.created_at DESC
        LIMIT :limit OFFSET :offset
        """,
            nativeQuery = true)
    List<Recommendation> findHistoryPicks(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Query(
            value =
                    """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT ON (r.game_id, r.market, r.side) r.rec_id
            FROM recommendations r
            JOIN slate_runs sr ON r.slate_run_id = sr.slate_run_id
            WHERE r.decision = 'medium'
              AND sr.run_date BETWEEN :startDate AND :endDate
            ORDER BY r.game_id, r.market, r.side, r.created_at DESC
        ) sub
        """,
            nativeQuery = true)
    long countHistoryPicks(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
