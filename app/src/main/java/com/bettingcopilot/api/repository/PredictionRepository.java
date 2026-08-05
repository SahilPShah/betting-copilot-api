package com.bettingcopilot.api.repository;

import com.bettingcopilot.api.entity.Prediction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    Optional<Prediction> findByGameId(String gameId);
}
