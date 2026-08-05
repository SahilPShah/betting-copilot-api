package com.bettingcopilot.api.controller;

import com.bettingcopilot.api.dto.HealthResponse;
import com.bettingcopilot.api.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Service and data freshness health check")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(
            summary = "Health check",
            description =
                    "Confirms database connectivity and reports the most recent game and slate run dates.")
    @ApiResponse(
            responseCode = "200",
            description = "Service is healthy",
            content = @Content(schema = @Schema(implementation = HealthResponse.class)))
    @GetMapping("/health")
    public HealthResponse getHealth() {
        return healthService.getHealth();
    }
}
