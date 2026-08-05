package com.bettingcopilot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Latest odds per market, keyed by side (home/away).")
public class OddsDto {
    @Schema(description = "Moneyline odds keyed by side.")
    private Map<String, OddsLineDto> moneyline;

    @Schema(description = "Run line odds keyed by side.")
    private Map<String, OddsLineDto> runLine;
}
