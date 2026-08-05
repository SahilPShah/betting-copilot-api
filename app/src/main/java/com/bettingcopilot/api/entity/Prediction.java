package com.bettingcopilot.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "predictions")
@Getter
@Setter
public class Prediction {
    @Id
    @Column(name = "prediction_id")
    private UUID predictionId;

    @Column(name = "game_id")
    private String gameId;

    @Column(name = "home_win_prob")
    private Double homeWinProb;

    @Column(name = "away_win_prob")
    private Double awayWinProb;

    @Column(name = "predicted_margin")
    private Double predictedMargin;

    @Column(name = "predicted_total")
    private Double predictedTotal;

    @Column(name = "home_cover_prob")
    private Double homeCoverProb;

    @Column(name = "away_cover_prob")
    private Double awayCoverProb;

    @Column(name = "elo_diff")
    private Double eloDiff;

    @Column(name = "model_version")
    private String modelVersion;
}
