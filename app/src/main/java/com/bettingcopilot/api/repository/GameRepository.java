package com.bettingcopilot.api.repository;

import com.bettingcopilot.api.entity.Game;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, String> {
    List<Game> findByGameDate(LocalDate date);

    Optional<Game> findTopByOrderByGameDateDesc();
}
