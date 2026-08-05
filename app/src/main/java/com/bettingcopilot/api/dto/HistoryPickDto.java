package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Historical pick with its outcome.")
public class HistoryPickDto {
    @Schema(description = "External game identifier.", example = "2026-04-22-CLE-TBR-1")
    private String gameId;

    @Schema(description = "Slate run date the pick came from.", example = "2026-04-22")
    private LocalDate runDate;

    @Schema(description = "Home team abbreviation.", example = "TBR")
    private String homeTeam;

    @Schema(description = "Away team abbreviation.", example = "CLE")
    private String awayTeam;

    @Schema(description = "Betting market.", example = "moneyline")
    private String market;

    @Schema(description = "Recommended side.", example = "away")
    private String side;

    @Schema(description = "American odds at recommendation time.", example = "-136")
    private Integer odds;

    @Schema(description = "Model edge over the market.", example = "0.347")
    private Double edge;

    @Schema(description = "Model confidence score (0-10).", example = "8.74")
    private Double confidence;

    @Schema(description = "Decision label assigned by the model.", example = "medium")
    private String decision;

    @Schema(
            description = "Pick outcome.",
            example = "WIN",
            allowableValues = {"WIN", "LOSS", "PENDING"})
    private String outcome;

    @Schema(description = "Final home score; null until the game is final.", example = "2")
    private Integer homeScore;

    @Schema(description = "Final away score; null until the game is final.", example = "5")
    private Integer awayScore;
}
