package com.bettingcopilot.api.contracts;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Smoke contract tests against a running server (-Dapi.baseUrl=...). They assert status codes and
 * top-level shape only — the server's data is unknown here.
 */
@Tag("contract")
class EndpointsContractIT extends AbstractContractTest {

    @Test
    void health_returnsOkStatus() {
        get("/health").then().statusCode(200).body("status", equalTo("ok"));
    }

    @Test
    void slate_todayEitherExistsOrIs404() {
        get("/slate").then().statusCode(anyOf(is(200), is(404)));
    }

    @Test
    void slate_unknownDate_returns404() {
        get("/slate/1999-01-01").then().statusCode(404);
    }

    @Test
    void game_unknownId_returns404() {
        get("/game/1999-01-01-XXX-YYY-1").then().statusCode(404);
    }

    @Test
    void history_returnsSummaryAndPagination() {
        get("/history")
                .then()
                .statusCode(200)
                .body("summary", notNullValue())
                .body("pagination.page", equalTo(1))
                .body("pagination.perPage", equalTo(20));
    }

    @Test
    void openApiDocs_available() {
        get("/v3/api-docs").then().statusCode(200).body("paths", notNullValue());
    }
}
