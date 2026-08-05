package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Aggregate outcome summary over the selected date range.")
public class HistorySummaryDto {
    @Schema(description = "Number of winning picks.", example = "15")
    private int wins;

    @Schema(description = "Number of losing picks.", example = "8")
    private int losses;

    @Schema(description = "Number of pushes.", example = "0")
    private int pushes;

    @Schema(description = "Number of picks on games not yet final.", example = "2")
    private int pending;

    @Schema(
            description = "wins / (wins + losses); null when no picks have resolved.",
            example = "0.652")
    private Double winRate;
}
