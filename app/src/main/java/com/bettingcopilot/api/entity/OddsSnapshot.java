package com.bettingcopilot.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "odds_snapshots")
@Getter
@Setter
public class OddsSnapshot {
    @Id
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "game_id")
    private String gameId;

    @Column(name = "bookmaker")
    private String bookmaker;

    @Column(name = "market")
    private String market;

    @Column(name = "side")
    private String side;

    @Column(name = "american_odds")
    private Integer americanOdds;

    @Column(name = "run_line_point")
    private Double runLinePoint;

    @Column(name = "implied_prob")
    private Double impliedProb;

    @Column(name = "captured_at")
    private OffsetDateTime capturedAt;

    @Column(name = "is_closing")
    private Boolean isClosing;
}
