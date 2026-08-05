package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Betting recommendation summary for a game.")
public class RecommendationSummaryDto {
    @Schema(description = "Betting market.", example = "moneyline")
    private String market;

    @Schema(description = "Recommended side.", example = "away")
    private String side;

    @Schema(description = "Model edge over the market.", example = "0.110")
    private Double edge;

    @Schema(description = "Model confidence score (0-10).", example = "7.3")
    private Double confidence;

    @Schema(description = "Decision label assigned by the model.", example = "medium")
    private String decision;

    @Schema(description = "Natural-language explanation for the recommendation.")
    private String llmExplanation;
}
