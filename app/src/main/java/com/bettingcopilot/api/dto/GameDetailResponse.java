package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
        description =
                "Full detail for a single game: prediction, latest odds, starters, and recommendation.")
public class GameDetailResponse {
    @Schema(description = "External game identifier.", example = "2026-04-29-CLE-TBR-1")
    private String gameId;

    @Schema(description = "Game date.", example = "2026-04-29")
    private LocalDate gameDate;

    @Schema(description = "Game status.", example = "scheduled")
    private String status;

    @Schema(description = "Home team abbreviation.", example = "TBR")
    private String homeTeam;

    @Schema(description = "Away team abbreviation.", example = "CLE")
    private String awayTeam;

    @Schema(description = "Scheduled first pitch time in UTC; often null.")
    private OffsetDateTime firstPitchUtc;

    @Schema(description = "Model prediction; null if no prediction exists for the game.")
    private PredictionDto prediction;

    @Schema(description = "Latest odds per market and side.")
    private OddsDto odds;

    @Schema(description = "Starting pitchers; null if no context data is available.")
    private StartersDto starters;

    @Schema(description = "Betting recommendation; null if the game has no selected pick.")
    private RecommendationSummaryDto recommendation;
}
