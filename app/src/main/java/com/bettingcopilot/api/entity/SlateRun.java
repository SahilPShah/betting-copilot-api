package com.bettingcopilot.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "slate_runs")
@Getter
@Setter
public class SlateRun {
    @Id
    @Column(name = "slate_run_id")
    private UUID slateRunId;

    @Column(name = "run_date")
    private LocalDate runDate;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "games_count")
    private Integer gamesCount;

    @Column(name = "picks_count")
    private Integer picksCount;

    @Column(name = "ran_at")
    private OffsetDateTime ranAt;
}
