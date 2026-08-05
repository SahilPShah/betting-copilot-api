package com.bettingcopilot.api.repository;

import com.bettingcopilot.api.entity.OddsSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OddsSnapshotRepository extends JpaRepository<OddsSnapshot, UUID> {

    @Query(
            value =
                    """
        SELECT DISTINCT ON (market, side) *
        FROM odds_snapshots
        WHERE game_id = :gameId
        ORDER BY market, side, captured_at DESC
        """,
            nativeQuery = true)
    List<OddsSnapshot> findLatestByGameId(@Param("gameId") String gameId);
}
