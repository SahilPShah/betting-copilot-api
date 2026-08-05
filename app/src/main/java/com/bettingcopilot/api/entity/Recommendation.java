package com.bettingcopilot.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recommendations")
@Getter
@Setter
public class Recommendation {
    @Id
    @Column(name = "rec_id")
    private UUID recId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slate_run_id")
    private SlateRun slateRun;

    @Column(name = "game_id")
    private String gameId;

    @Column(name = "market")
    private String market;

    @Column(name = "side")
    private String side;

    @Column(name = "edge")
    private Double edge;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "decision")
    private String decision;

    @Column(name = "no_bet_reason")
    private String noBetReason;

    @Column(name = "context_snapshot", columnDefinition = "jsonb")
    private String contextSnapshot;

    @Column(name = "llm_explanation")
    private String llmExplanation;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
