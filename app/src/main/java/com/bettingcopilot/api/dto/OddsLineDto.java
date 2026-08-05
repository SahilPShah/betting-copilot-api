package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Latest odds line for one side of a market.")
public class OddsLineDto {
    @Schema(description = "American odds.", example = "-122")
    private Integer americanOdds;

    @Schema(description = "Vig-removed implied probability.", example = "0.526")
    private Double impliedProb;

    @Schema(description = "Run line point; null for moneyline.", example = "-1.5")
    private Double point;
}
