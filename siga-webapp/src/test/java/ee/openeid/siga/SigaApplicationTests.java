package ee.openeid.siga;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.SecurityConfiguration;
import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.auth.properties.SigaVaultProperties;
import ee.openeid.siga.webapp.json.UploadHashCodeContainerResponse;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static ee.openeid.siga.auth.filter.hmac.HmacHeaders.*;
import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {SigaApplication.class, SigaApplicationTests.TestConfiguration.class}, webEnvironment = RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "siga.security.hmac.expiration=120",
                "siga.security.hmac.clock-skew=2"})

@AutoConfigureMockMvc
public class SigaApplicationTests {

    private final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    private final static String HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    private final static String REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    private String xAuthorizationTimestamp;
    private String xAuthorizationSignature;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    VaultTemplate vaultTemplate;

    @Before
    public void setup() {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
    }

    @Test
    public void mobileIdSigning() throws Exception {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
        String containerId = uploadContainer();


    }

    private void startMobileSigning() {

    }

    private String uploadContainer() throws Exception {
        JSONObject request = new JSONObject();
        String container = IOUtils.toString(getFileInputStream("hashcode.asice"), Charset.defaultCharset());
        request.put("container", container);
        setSignature(request.toString());
        ResultActions response = mockMvc.perform(post("/upload/hashcodecontainers")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), REQUESTING_SERVICE_UUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), xAuthorizationSignature)
                .content(request.toString().getBytes()))
                .andExpect(status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), UploadHashCodeContainerResponse.class).getContainerId();
    }

    private void setSignature(String payload) throws Exception {
        xAuthorizationSignature = HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(REQUESTING_SERVICE_UUID)
                .timestamp(xAuthorizationTimestamp)
                .payload(payload.getBytes())
                .build().getSignature(HMAC_SHARED_SECRET);
    }

    private InputStream getFileInputStream(String filePath) throws URISyntaxException, IOException {
        Path documentPath = Paths.get(new ClassPathResource("hashcode.asice").getURI());
        return new ByteArrayInputStream(Base64.getEncoder().encode(Files.readAllBytes(documentPath)));
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
    }

}

