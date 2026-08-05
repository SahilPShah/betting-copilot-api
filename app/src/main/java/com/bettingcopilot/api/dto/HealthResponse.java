package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Service health with the most recent game and slate dates in the database.")
public class HealthResponse {
    @Schema(description = "Service status.", example = "ok")
    private String status;

    @Schema(
            description = "Most recent game date in the database; null if no games exist.",
            example = "2026-04-29")
    private LocalDate latestGameDate;

    @Schema(
            description = "Most recent slate run date in the database; null if no runs exist.",
            example = "2026-04-29")
    private LocalDate latestSlateDate;
}
