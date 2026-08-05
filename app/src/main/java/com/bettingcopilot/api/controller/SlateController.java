package com.bettingcopilot.api.controller;

import com.bettingcopilot.api.dto.SlateResponse;
import com.bettingcopilot.api.service.SlateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Slate", description = "Prediction and recommendation slate endpoints")
public class SlateController {
    private final SlateService slateService;

    public SlateController(SlateService slateService) {
        this.slateService = slateService;
    }

    @Operation(
            summary = "Get today's slate",
            description =
                    "Returns predictions, recommendations, and no-bet outcomes for the current server date.")
    @ApiResponse(
            responseCode = "200",
            description = "Slate returned successfully",
            content = @Content(schema = @Schema(implementation = SlateResponse.class)))
    @GetMapping("/slate")
    public SlateResponse getToday() {
        return slateService.getSlate(LocalDate.now());
    }

    @Operation(
            summary = "Get slate by date",
            description =
                    "Returns predictions, recommendations, and no-bet outcomes for a specific slate date.")
    @ApiResponse(
            responseCode = "200",
            description = "Slate returned successfully",
            content = @Content(schema = @Schema(implementation = SlateResponse.class)))
    @GetMapping("/slate/{date}")
    public SlateResponse getByDate(
            @Parameter(
                            description = "Slate date in ISO-8601 format.",
                            example = "2026-05-01",
                            required = true)
                    @PathVariable
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        return slateService.getSlate(date);
    }
}
