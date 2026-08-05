package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Starting pitcher snapshot.")
public class StarterDto {
    @Schema(description = "Pitcher name.", example = "Gavin Williams")
    private String name;

    @Schema(description = "Season ERA.", example = "3.90")
    private Double era;

    @Schema(description = "ERA over the last three starts.", example = "4.50")
    private Double l3Era;

    @Schema(description = "Season WHIP.", example = "1.167")
    private Double whip;
}
