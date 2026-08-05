package com.bettingcopilot.api.controller;

import com.bettingcopilot.api.dto.GameDetailResponse;
import com.bettingcopilot.api.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Game", description = "Single game detail endpoints")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @Operation(
            summary = "Get game detail",
            description =
                    "Returns full detail for a single game: prediction, latest odds, starting pitchers, and recommendation.")
    @ApiResponse(
            responseCode = "200",
            description = "Game detail returned successfully",
            content = @Content(schema = @Schema(implementation = GameDetailResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No game exists with the given identifier",
            content = @Content)
    @GetMapping("/game/{gameId}")
    public GameDetailResponse getGame(
            @Parameter(
                            description = "Game identifier in YYYY-MM-DD-HOME-AWAY-N format.",
                            example = "2026-04-29-CLE-TBR-1",
                            required = true)
                    @PathVariable
                    String gameId) {
        return gameService.getGame(gameId);
    }
}
