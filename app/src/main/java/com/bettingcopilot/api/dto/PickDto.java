package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Recommended pick for a game and betting market.")
public class PickDto {
    @Schema(description = "External game identifier.", example = "mlb-20260501-nyy-bos")
    private String gameId;

    @Schema(description = "Home team name or abbreviation.", example = "BOS")
    private String homeTeam;

    @Schema(description = "Away team name or abbreviation.", example = "NYY")
    private String awayTeam;

    @Schema(description = "Scheduled first pitch time in UTC.")
    private OffsetDateTime firstPitchUtc;

    @Schema(description = "Betting market for the pick.", example = "moneyline")
    private String market;

    @Schema(description = "Recommended side in the market.", example = "BOS")
    private String side;

    @Schema(description = "American odds for the recommended side.", example = "-125")
    private Integer odds;

    @Schema(description = "Estimated model edge over the market.", example = "0.043")
    private Double edge;

    @Schema(description = "Model confidence score.", example = "0.71")
    private Double confidence;

    @Schema(description = "Model probability for the recommended side.", example = "0.598")
    private Double modelProb;

    @Schema(description = "Market-implied probability from the listed odds.", example = "0.556")
    private Double impliedProb;

    @Schema(description = "Decision label assigned by the model.", example = "PICK")
    private String decision;

    @Schema(description = "Projected home starting pitcher.", example = "Brayan Bello")
    private String homeStarter;

    @Schema(description = "Home starter season ERA.", example = "3.48")
    private Double homeStarterEra;

    @Schema(description = "Home starter ERA over the last three starts.", example = "2.95")
    private Double homeStarterL3Era;

    @Schema(description = "Home starter season WHIP.", example = "1.18")
    private Double homeStarterWhip;

    @Schema(description = "Projected away starting pitcher.", example = "Gerrit Cole")
    private String awayStarter;

    @Schema(description = "Away starter season ERA.", example = "3.12")
    private Double awayStarterEra;

    @Schema(description = "Away starter ERA over the last three starts.", example = "3.35")
    private Double awayStarterL3Era;

    @Schema(description = "Away starter season WHIP.", example = "1.05")
    private Double awayStarterWhip;

    @Schema(description = "Natural-language explanation for the recommendation.")
    private String llmExplanation;
}
