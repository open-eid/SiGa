package ee.openeid.siga.common.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@WireMockTest
class HttpClientImplTest {
    private HttpClientImpl httpClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMockServer) {
        String requestUrl = "http://localhost:" + wireMockServer.getHttpPort();

        WebClient webClient = WebClient.builder()
                .baseUrl(requestUrl)
                .build();

        httpClient = new HttpClientImpl(webClient);
    }

    @AfterEach
    void tearDown() {
        WireMock.reset();
    }

    @Test
    void getMethodWithOkStatusCode() {
        WireMock.stubFor(
                WireMock.get("/path").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Test message\"}")
                        .withStatus(200))
        );

        String response = httpClient.get("/path", String.class);

        assertEquals("{\"message\": \"Test message\"}", response);
    }

    @Test
    void postMethodWithOkStatusCode() {
        WireMock.stubFor(
                WireMock.post("/path")
                        .withRequestBody(equalToJson("{\"key\": \"value\"}"))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"message\": \"Success\"}")
                                .withStatus(200))
        );

        String requestBody = "{\"key\": \"value\"}";
        String response = httpClient.post("/path", requestBody, String.class);

        assertEquals("{\"message\": \"Success\"}", response);
    }

    @ParameterizedTest
    @EnumSource(
            value = HttpStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"OK", "CONTINUE", "SWITCHING_PROTOCOLS", "PROCESSING", "EARLY_HINTS", "CHECKPOINT"}
    )
    void getMethodWithNon1xxandNonOkStatusCodesWithoutBody(HttpStatus status) {
        WireMock.stubFor(
                WireMock.get("/path").willReturn(WireMock.aResponse()
                        .withStatus(status.value()))
        );

        HttpStatusException caughtException = assertThrows(
                HttpStatusException.class, () -> httpClient.get("/path", String.class)
        );
        assertEquals(status.value(), caughtException.getHttpStatus().value());
        assertArrayEquals("".getBytes(StandardCharsets.UTF_8), caughtException.getResponseBody());
    }

    @ParameterizedTest
    @EnumSource(
            value = HttpStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"OK", "CONTINUE", "SWITCHING_PROTOCOLS", "PROCESSING", "EARLY_HINTS", "CHECKPOINT", "NO_CONTENT", "NOT_MODIFIED"}
    )
    void getMethodWithNon1xxandNonOkStatusCodesWithBody(HttpStatus status) {
        WireMock.stubFor(
                WireMock.get("/path").willReturn(WireMock.aResponse()
                        .withStatus(status.value())
                        .withBody("{\"message\": \"Error message\"}"))
        );

        HttpStatusException caughtException = assertThrows(
                HttpStatusException.class, () -> httpClient.get("/path", String.class)
        );
        assertEquals(status.value(), caughtException.getHttpStatus().value());
        assertArrayEquals("{\"message\": \"Error message\"}".getBytes(StandardCharsets.UTF_8), caughtException.getResponseBody());
    }

    @ParameterizedTest
    @EnumSource(
            value = HttpStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"OK", "CONTINUE", "SWITCHING_PROTOCOLS", "PROCESSING", "EARLY_HINTS", "CHECKPOINT"}
    )
    void postMethodWithNon1xxandNonOkStatusCodesWithoutBody(HttpStatus status) {
        WireMock.stubFor(
                WireMock.post("/path").willReturn(WireMock.aResponse()
                        .withStatus(status.value()))
        );

        String requestBody = "{\"key\": \"value\"}";

        HttpStatusException caughtException = assertThrows(
                HttpStatusException.class, () -> httpClient.post("/path", requestBody, String.class)
        );
        assertEquals(status.value(), caughtException.getHttpStatus().value());
        assertArrayEquals("".getBytes(StandardCharsets.UTF_8), caughtException.getResponseBody());
    }

    @ParameterizedTest
    @EnumSource(
            value = HttpStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"OK", "CONTINUE", "SWITCHING_PROTOCOLS", "PROCESSING", "EARLY_HINTS", "CHECKPOINT", "NO_CONTENT", "NOT_MODIFIED"}
    )
    void postMethodWithNon1xxandNonOkStatusCodesWithBody(HttpStatus status) {
        WireMock.stubFor(
                WireMock.post("/path").willReturn(WireMock.aResponse()
                            .withBody("{\"message\": \"Error message\"}")
                            .withStatus(status.value()))
        );

        String requestBody = "{\"key\": \"value\"}";

        HttpStatusException caughtException = assertThrows(
                HttpStatusException.class, () -> httpClient.post("/path", requestBody, String.class)
        );
        assertEquals(status.value(), caughtException.getHttpStatus().value());
        assertArrayEquals("{\"message\": \"Error message\"}".getBytes(StandardCharsets.UTF_8), caughtException.getResponseBody());
    }

    @Test
    void getMethodWithParsingObjectToJsonAndBack() {
        String responseBody = "{\"name\": \"name\", \"age\": 5}";

        WireMock.stubFor(
                WireMock.get("/path").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                        .withStatus(200))
        );

        SampleData response = httpClient.get("/path", SampleData.class);

        assertEquals("name", response.getName());
        assertEquals(5, response.getAge());
    }

    @Test
    void postMethodWithParsingJsonToObject() {
        String requestBody = "{\"name\": \"name\", \"age\": 5}";

        WireMock.stubFor(
                WireMock.post("/path").withRequestBody(equalToJson(requestBody))
                        .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(requestBody)
                        .withStatus(200))
        );

        SampleData response = httpClient.post("/path", requestBody, SampleData.class);

        assertEquals("name", response.getName());
        assertEquals(5, response.getAge());
    }

    @Test
    void postMethodWithParsingObjectToJsonAndBack() {
        String requestBody = "{\"name\": \"name\", \"age\": 5}";
        SampleData sampleData = new SampleData("name", 5);

        WireMock.stubFor(
                WireMock.post("/path").withRequestBody(equalToJson(requestBody))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(requestBody)
                                .withStatus(200))
        );

        SampleData response = httpClient.post("/path", sampleData, SampleData.class);

        assertEquals(sampleData.name, response.getName());
        assertEquals(sampleData.age, response.getAge());
    }

    @Test
    void postMethodWithInvalidRequestBody() {
        String requestBody = "{\"place\": \"place\", \"length\": 5}";

        WireMock.stubFor(
                WireMock.post("/path").withRequestBody(equalToJson(requestBody))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(requestBody)
                                .withStatus(200))
        );

        assertThrows(HttpStatusException.class, () -> httpClient.post("/path", "Invalid request", String.class));
    }

    @Test
    void getMethodWithBytesResponse() {
        byte[] responseBody = "Test message".getBytes();

        WireMock.stubFor(
                WireMock.get("/path").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody(responseBody)
                        .withStatus(200))
        );

        byte[] response = httpClient.get("/path", byte[].class);

        assertArrayEquals(responseBody, response);
    }

    @Test
    void postMethodWithBytesResponse() {
        byte[] body = "Test message".getBytes();

        WireMock.stubFor(
                WireMock.post("/path").withRequestBody(binaryEqualTo(body))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/octet-stream")
                                .withBody(body)
                                .withStatus(200))
        );

        byte[] response = httpClient.post("/path", body, byte[].class);
        assertArrayEquals(body, response);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SampleData {
        private String name;
        private int age;
    }
}
