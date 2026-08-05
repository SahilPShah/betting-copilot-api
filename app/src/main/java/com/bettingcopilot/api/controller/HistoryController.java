package com.bettingcopilot.api.controller;

import com.bettingcopilot.api.dto.HistoryResponse;
import com.bettingcopilot.api.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "History", description = "Historical pick performance endpoints")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @Operation(
            summary = "Get pick history",
            description =
                    "Returns past picks in a date range with WIN/LOSS/PENDING outcomes computed from final scores.")
    @ApiResponse(
            responseCode = "200",
            description = "History returned successfully",
            content = @Content(schema = @Schema(implementation = HistoryResponse.class)))
    @GetMapping("/history")
    public HistoryResponse getHistory(
            @Parameter(
                            description = "Range start date (inclusive); defaults to 30 days ago.",
                            example = "2026-04-01")
                    @RequestParam(name = "start_date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @Parameter(
                            description = "Range end date (inclusive); defaults to today.",
                            example = "2026-04-29")
                    @RequestParam(name = "end_date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            @Parameter(description = "Page number (1-based).", example = "1")
                    @RequestParam(name = "page", defaultValue = "1")
                    int page,
            @Parameter(description = "Results per page (max 100).", example = "20")
                    @RequestParam(name = "per_page", defaultValue = "20")
                    int perPage) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        int safePage = Math.max(page, 1);
        int safePerPage = Math.clamp(perPage, 1, 100);

        return historyService.getHistory(start, end, safePage, safePerPage);
    }
}
