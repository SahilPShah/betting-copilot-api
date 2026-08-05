package com.bettingcopilot.api.repository;

import com.bettingcopilot.api.entity.SlateRun;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlateRunRepository extends JpaRepository<SlateRun, UUID> {
    Optional<SlateRun> findByRunDate(LocalDate runDate);

    Optional<SlateRun> findTopByOrderByRanAtDesc();
}
