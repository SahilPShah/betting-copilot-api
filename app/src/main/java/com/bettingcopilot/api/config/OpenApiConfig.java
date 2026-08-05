package com.bettingcopilot.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Betting Copilot API",
                        version = "0.0.1",
                        description =
                                "Read-only REST API for Betting Copilot predictions and recommendations",
                        contact = @Contact(name = "Betting Copilot")))
public class OpenApiConfig {}
