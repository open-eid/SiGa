package ee.openeid.siga.auth;

import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.auth.properties.SigaVaultProperties;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.auth.filter.hmac.HmacHeaders.*;
import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {SigaAuthTests.TestConfiguration.class}, webEnvironment = RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "siga.security.hmac.expiration=120",
                "siga.security.hmac.clock-skew=2"})
@AutoConfigureMockMvc
public class SigaAuthTests {

    private final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    private final static String HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    private final static String REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    private final static int TOKEN_EXPIRATION_IN_SECONDS = 120;
    private final static int TOKEN_CLOCK_SKEW = 2;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    VaultTemplate vaultTemplate;
    private String xAuthorizationTimestamp;
    private String xAuthorizationSignature;

    @Before
    public void setup() throws InvalidKeyException, NoSuchAlgorithmException {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        xAuthorizationSignature = HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(REQUESTING_SERVICE_UUID)
                .timestamp(xAuthorizationTimestamp)
                .build().getSignature(HMAC_SHARED_SECRET);
    }

    @Test
    public void accessShouldBeAuthorized_WithValidAuthenticationToken() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature))
                .andExpect(status().isNotFound());
    }

    @Test
    public void accessShouldBeAuthorized_WithValidAuthenticationTokenAndPayload() throws Exception {

        String payload = "{\"test\":\"value\"}";
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        xAuthorizationSignature = HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(REQUESTING_SERVICE_UUID)
                .timestamp(xAuthorizationTimestamp)
                .payload(payload.getBytes())
                .build().getSignature(HMAC_SHARED_SECRET);

        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature)
                .content(payload.getBytes()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void accessShouldBeUnauthorized_WithoutXAuthorizationClientIdHeader() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithoutXAuthorizationTimestampHeader() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), "")
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithoutXAuthorizationSignatureHeader() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), "")
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithoutHmacToken() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), "")
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithInvalidHmacSignature() throws Exception {
        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(),
                        "2c4a5baedfc1ebae179ed5be3a1855aea28f5fbab7d2bd1a957bf8822f6f2f40"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid HMAC signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithInvalidClientId() throws Exception {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        xAuthorizationSignature = HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid("invalid")
                .timestamp(xAuthorizationTimestamp)
                .build().getSignature(HMAC_SHARED_SECRET);

        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), "invalid")
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Bad credentials"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void accessShouldBeUnauthorized_WithExpiredHmacToken() throws Exception {
        xAuthorizationTimestamp = valueOf(now().minusSeconds(TOKEN_EXPIRATION_IN_SECONDS +
                TOKEN_CLOCK_SKEW).getEpochSecond());
        xAuthorizationSignature = HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(REQUESTING_SERVICE_UUID)
                .timestamp(xAuthorizationTimestamp)
                .build().getSignature(HMAC_SHARED_SECRET);

        mockMvc.perform(get("/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("HMAC token is expired"))
                .andExpect(status().isUnauthorized());
    }

    @Profile("test")
    @Configuration
    @Import(SecurityConfiguration.class)
    static class TestConfiguration {

        @Primary
        @Bean
        public VaultTemplate vaultTemplate() {
            VaultTemplate vaultTemplate = Mockito.mock(VaultTemplate.class);
            SigaVaultProperties svp = new SigaVaultProperties();
            svp.setJasyptEncryptionConf(new SigaVaultProperties.JasyptEncryptionConf());
            svp.getJasyptEncryptionConf().setAlgorithm("PBEWithMD5AndDES");
            svp.getJasyptEncryptionConf().setKey("encryptorKey");
            VaultResponseSupport<SigaVaultProperties> vrs = new VaultResponseSupport<>();
            vrs.setData(svp);
            Mockito.when(vaultTemplate.read("dev/siga", SigaVaultProperties.class)).thenReturn(vrs);
            return vaultTemplate;
        }

        @Bean(destroyMethod = "close")
        public Ignite ignite() throws IgniteException {
            return Ignition.start("ignite-test-configuration.xml");
        }
    }

}

