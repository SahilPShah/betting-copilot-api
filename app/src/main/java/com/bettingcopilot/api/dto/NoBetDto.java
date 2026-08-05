package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Game evaluated without a betting recommendation.")
public class NoBetDto {
    @Schema(description = "External game identifier.", example = "mlb-20260501-lad-sf")
    private String gameId;

    @Schema(
            description = "Reason no betting recommendation was made.",
            example = "No market edge above threshold.")
    private String reason;
}
