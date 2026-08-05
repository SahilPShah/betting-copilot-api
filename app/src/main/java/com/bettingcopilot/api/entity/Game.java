package com.bettingcopilot.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "games")
@Getter
@Setter
public class Game {
    @Id
    @Column(name = "game_id")
    private String gameId;

    @Column(name = "game_date")
    private LocalDate gameDate;

    @Column(name = "home_team_id")
    private String homeTeamId;

    @Column(name = "away_team_id")
    private String awayTeamId;

    @Column(name = "status")
    private String status;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "first_pitch_utc")
    private OffsetDateTime firstPitchUtc;
}
