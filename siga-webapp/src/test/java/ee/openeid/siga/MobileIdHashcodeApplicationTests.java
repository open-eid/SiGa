package ee.openeid.siga;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles({"test", "digidoc4jTest", "mobileId"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
public class MobileIdHashcodeApplicationTests extends MobileIdBaseApplicationTests {

    @Test
    public void mobileIdDatafileStartMobileIdSigningFailsWith404() throws Exception {
        postRequest(
                "/containers/" + UUID.randomUUID() + "/mobileidsigning",
                createStartMobileSigningRequest(),
                status().isNotFound()
        );
    }

    @Test
    public void mobileIdDatafileGetMobileIdSigningStatusFailsWith404() throws Exception {
        getRequest(
                "/containers/" + UUID.randomUUID() + "/mobileidsigning/" + UUID.randomUUID() + "/status",
                status().isNotFound()
        );
    }

}
