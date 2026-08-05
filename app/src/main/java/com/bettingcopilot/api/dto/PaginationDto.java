package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Pagination metadata.")
public class PaginationDto {
    @Schema(description = "Current page (1-based).", example = "1")
    private int page;

    @Schema(description = "Results per page.", example = "20")
    private int perPage;

    @Schema(description = "Total matching picks across all pages.", example = "25")
    private long total;
}
