package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Model prediction for a game.")
public class PredictionDto {
    @Schema(description = "Model probability that the home team wins.", example = "0.416")
    private Double homeWinProb;

    @Schema(description = "Model probability that the away team wins.", example = "0.584")
    private Double awayWinProb;

    @Schema(
            description = "Predicted run margin; positive means home wins by that many runs.",
            example = "1.2")
    private Double predictedMargin;

    @Schema(description = "Predicted total runs scored.", example = "8.5")
    private Double predictedTotal;

    @Schema(description = "Probability that the home team covers -1.5.", example = "0.46")
    private Double homeCoverProb;

    @Schema(description = "Probability that the away team covers +1.5.", example = "0.54")
    private Double awayCoverProb;

    @Schema(description = "Home Elo minus away Elo at prediction time.", example = "-22.1")
    private Double eloDiff;
}
