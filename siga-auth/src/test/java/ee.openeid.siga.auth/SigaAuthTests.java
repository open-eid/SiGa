package ee.openeid.siga.auth;

import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.*;
import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {SigaAuthTests.TestConfiguration.class}, webEnvironment = RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "siga.security.hmac.expiration=120",
                "siga.security.hmac.clock-skew=2"})
@AutoConfigureMockMvc
public class SigaAuthTests {

    final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    final static String HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    final static String REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    final static int TOKEN_EXPIRATION_IN_SECONDS = 120;
    final static int TOKEN_CLOCK_SKEW = 2;
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void accessShouldBeAuthorized_WithValidToken() throws Exception {
        mockMvc.perform(buildRequest(get("/"), "/")).andExpect(status().isNotFound());
    }

    @Test
    public void accessShouldBeAuthorized_WithValidTokenAndPayload() throws Exception {
        mockMvc.perform(buildRequest(get("/"), "/", "{\"test\":\"value\"}".getBytes())).andExpect(status().isNotFound());
    }

    @Test
    public void accessShouldBeUnauthorized_WithNonMatchingRequestUriComparedToTokenSignature() throws Exception {
        mockMvc.perform(buildRequest(get("/path/not/specified/in/hmac"), "/"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC signature"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(buildRequest(get("/"), "/path/not/specified/in/hmac"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeAuthorized_WithMatchingRequestUriComparedToTokenSignature() throws Exception {
        mockMvc.perform(buildRequest(get("/path/specified/in/hmac"), "/path/specified/in/hmac"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void accessShouldBeUnauthorized_WithNonMatchingRequestParametersComparedToTokenSignature() throws Exception {
        mockMvc.perform(buildRequest(get("/?parameter_name=parameter_value"), "/"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC signature"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(buildRequest(get("/"), "/?parameter_name=parameter_value"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeAuthorized_WithMatchingRequestParametersComparedToTokenSignature() throws Exception {
        mockMvc.perform(buildRequest(get("/?parameter_name=parameter_value"), "/?parameter_name=parameter_value"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void accessShouldBeUnauthorized_WithNonMatchingRequestMethodComparedToTokenSignature() throws Exception {
        mockMvc.perform(buildRequest(post("/"), "/"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithInvalidXAuthorizationHmacAlgorithmHeader() throws Exception {
        mockMvc.perform(buildRequest(get("/"), "/")
                .header(X_AUTHORIZATION_HMAC_ALGORITHM.getValue(), "HmacSHA111"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC algorithm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithoutXAuthorizationServiceUuidHeader() throws Exception {
        String xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        String xAuthorizationSignature = buildSignature(xAuthorizationTimestamp);
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Missing X-Authorization-ServiceUuid header"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithoutXAuthorizationTimestampHeader() throws Exception {
        String xAuthorizationSignature = buildSignature(valueOf(now().getEpochSecond()));
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Missing X-Authorization-Timestamp header"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithInvalidXAuthorizationTimestampFormat() throws Exception {
        String xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        xAuthorizationTimestamp = xAuthorizationTimestamp.substring(0, xAuthorizationTimestamp.length() - 1);
        String xAuthorizationSignature = buildSignature(valueOf(now().getEpochSecond()));
        ResultMatcher expectErrorMessage = MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid X-Authorization-Timestamp format");
        performRequestWithRequiredHeaders(xAuthorizationTimestamp, xAuthorizationSignature, expectErrorMessage);

        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        xAuthorizationTimestamp += "0";
        xAuthorizationSignature = buildSignature(valueOf(now().getEpochSecond()));
        performRequestWithRequiredHeaders(xAuthorizationTimestamp, xAuthorizationSignature, expectErrorMessage);

        xAuthorizationTimestamp = "123456789a";
        xAuthorizationSignature = buildSignature(valueOf(now().getEpochSecond()));
        performRequestWithRequiredHeaders(xAuthorizationTimestamp, xAuthorizationSignature, expectErrorMessage);

        xAuthorizationTimestamp = "";
        xAuthorizationSignature = buildSignature(valueOf(now().getEpochSecond()));
        performRequestWithRequiredHeaders(xAuthorizationTimestamp, xAuthorizationSignature, expectErrorMessage);
    }

    private void performRequestWithRequiredHeaders(String xAuthorizationTimestamp, String xAuthorizationSignature, ResultMatcher resultMatcher) throws Exception {
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature))
                .andExpect(resultMatcher)
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithoutXAuthorizationSignatureHeader() throws Exception {
        String xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Missing X-Authorization-Signature header"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithInvalidSignature() throws Exception {
        mockMvc.perform(buildRequest(get("/").header(X_AUTHORIZATION_SIGNATURE.getValue(), "2c4a5baedfc1ebae179ed5be3a1855aea28f5fbab7d2bd1a957bf8822f6f2f40"), "/"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC signature"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    public void accessShouldBeUnauthorized_WithInvalidServiceUuid() throws Exception {
        mockMvc.perform(buildRequest(get("/").header(X_AUTHORIZATION_SERVICE_UUID.getValue(), "12345678900123456789012345678901"), "/"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Bad credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithTokenTimestampInFuture() throws Exception {
        String xAuthorizationTimestamp = valueOf(now().plusSeconds(TOKEN_CLOCK_SKEW).plusSeconds(10).getEpochSecond());
        String xAuthorizationSignature = buildSignature(xAuthorizationTimestamp);
        performRequestWithRequiredHeaders(xAuthorizationTimestamp, xAuthorizationSignature, MockMvcResultMatchers.jsonPath("$.errorMessage").value("HMAC token is expired"));
    }

    @Test
    public void accessShouldBeUnauthorized_WithExpiredToken() throws Exception {
        String xAuthorizationTimestamp = valueOf(now().minusSeconds(TOKEN_EXPIRATION_IN_SECONDS + TOKEN_CLOCK_SKEW).getEpochSecond());
        String xAuthorizationSignature = buildSignature(xAuthorizationTimestamp);
        performRequestWithRequiredHeaders(xAuthorizationTimestamp, xAuthorizationSignature, MockMvcResultMatchers.jsonPath("$.errorMessage").value("HMAC token is expired"));
    }

    private MockHttpServletRequestBuilder buildRequest(MockHttpServletRequestBuilder builder, String uriForSignatureBuilder) throws InvalidKeyException, NoSuchAlgorithmException {
        return buildRequest(builder, uriForSignatureBuilder, null);
    }

    private MockHttpServletRequestBuilder buildRequest(MockHttpServletRequestBuilder builder, String uriForSignatureBuilder, byte[] payload) throws InvalidKeyException, NoSuchAlgorithmException {
        String xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        String xAuthorizationSignature = buildSignature(xAuthorizationTimestamp, uriForSignatureBuilder, payload);
        return builder.accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature)
                .content(payload);
    }

    private String buildSignature(String xAuthorizationTimestamp) throws NoSuchAlgorithmException, InvalidKeyException {
        return buildSignature(xAuthorizationTimestamp, "/", null);
    }

    private String buildSignature(String xAuthorizationTimestamp, String uriForSignatureBuilder, byte[] payload) throws NoSuchAlgorithmException, InvalidKeyException {
        return HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(REQUESTING_SERVICE_UUID)
                .timestamp(xAuthorizationTimestamp)
                .requestMethod("GET")
                .uri(uriForSignatureBuilder)
                .payload(payload)
                .build().getSignature(HMAC_SHARED_SECRET);
    }

    @Profile("test")
    @Configuration
    @Import(SecurityConfiguration.class)
    @ComponentScan(basePackages = {"ee.openeid.siga.auth", "ee.openeid.siga.common"})
    static class TestConfiguration {
        @Bean(destroyMethod = "close")
        public Ignite ignite() throws IgniteException {
            return Ignition.start("ignite-test-configuration.xml");
        }
    }
}

