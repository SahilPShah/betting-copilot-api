package com.bettingcopilot.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@Tag("integration")
class OpenApiDocumentationIT extends AbstractIntegrationTest {

    @Autowired RestTestClient restTestClient;

    @Test
    void openApiDocs_includeAllEndpoints() {
        Map<?, ?> body =
                restTestClient
                        .get()
                        .uri("/v3/api-docs")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(Map.class)
                        .returnResult()
                        .getResponseBody();

        assertNotNull(body);
        Object pathsObject = body.get("paths");
        assertTrue(pathsObject instanceof Map<?, ?>);

        Map<?, ?> paths = (Map<?, ?>) pathsObject;
        assertTrue(paths.containsKey("/health"));
        assertTrue(paths.containsKey("/slate"));
        assertTrue(paths.containsKey("/slate/{date}"));
        assertTrue(paths.containsKey("/game/{gameId}"));
        assertTrue(paths.containsKey("/history"));
    }
}
