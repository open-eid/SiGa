package ee.openeid.siga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "digidoc4jTest", "smartId"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
class SmartIdHashcodeApplicationTests extends SmartIdBaseApplicationTests {

    @Test
    void smartIdDatafileStartSmartIdCertificateChoiceFailsWith404() throws Exception {
        postRequest(
                "/containers/" + UUID.randomUUID() + "/smartidsigning/certificatechoice",
                createStartSmartIdCertificateChoiceRequest(),
                status().isNotFound()
        );
    }

    @Test
    void smartIdDatafileStartSmartIdSigningFailsWith404() throws Exception {
        postRequest(
                "/containers/" + UUID.randomUUID() + "/smartidsigning",
                createStartSmartIdSigningRequest(null),
                status().isNotFound()
        );
    }

    @Test
    void smartIdDatafileGetSmartIdStatusFailsWith404() throws Exception {
        getRequest(
                "/containers/" + UUID.randomUUID() + "/smartidsigning/" + UUID.randomUUID() + "/status",
                status().isNotFound()
        );
    }

}
