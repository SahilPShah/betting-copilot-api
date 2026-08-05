package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Starting pitchers for both sides.")
public class StartersDto {
    @Schema(description = "Home starting pitcher; null if unknown.")
    private StarterDto home;

    @Schema(description = "Away starting pitcher; null if unknown.")
    private StarterDto away;
}
