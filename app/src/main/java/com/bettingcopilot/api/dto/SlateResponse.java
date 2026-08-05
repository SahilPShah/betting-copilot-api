package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Daily betting slate with recommended picks and no-bet games.")
public class SlateResponse {
    @Schema(description = "Slate date.", example = "2026-05-01")
    private LocalDate runDate;

    @Schema(description = "Model version used to generate the slate.", example = "mlb-v1.0.0")
    private String modelVersion;

    @Schema(description = "Number of games evaluated for the slate.", example = "15")
    private Integer gamesCount;

    @Schema(description = "Number of recommended picks returned.", example = "4")
    private Integer picksCount;

    @Schema(description = "Timestamp when the slate run completed.")
    private OffsetDateTime ranAt;

    @ArraySchema(
            schema = @Schema(implementation = PickDto.class),
            arraySchema = @Schema(description = "Recommended betting picks."))
    private List<PickDto> picks;

    @ArraySchema(
            schema = @Schema(implementation = NoBetDto.class),
            arraySchema =
                    @Schema(description = "Games evaluated without a betting recommendation."))
    private List<NoBetDto> noBets;
}
