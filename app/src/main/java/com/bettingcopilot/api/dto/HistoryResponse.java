package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Past picks with WIN/LOSS/PENDING outcomes computed from final scores.")
public class HistoryResponse {
    @Schema(description = "Aggregate outcome summary for the date range.")
    private HistorySummaryDto summary;

    @ArraySchema(
            schema = @Schema(implementation = HistoryPickDto.class),
            arraySchema = @Schema(description = "Picks in the current page."))
    private List<HistoryPickDto> picks;

    @Schema(description = "Pagination metadata.")
    private PaginationDto pagination;
}
