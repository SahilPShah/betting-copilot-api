package com.bettingcopilot.api.contracts;

import io.restassured.RestAssured;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractContractTest {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    @BeforeEach
    void configureRestAssured() {
        URI baseUri = URI.create(System.getProperty("api.baseUrl", DEFAULT_BASE_URL));
        RestAssured.baseURI = baseUri.getScheme() + "://" + baseUri.getAuthority();
        RestAssured.basePath = baseUri.getPath().equals("/") ? "" : baseUri.getPath();
    }
}
