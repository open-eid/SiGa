package ee.openeid.siga;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@TestPropertySource(locations={"classpath:application-test.properties"})
@SpringBootTest(classes = SigaApplication.class, webEnvironment=RANDOM_PORT)
@ActiveProfiles("test")
public class SigaApplicationTests {

	@Test
	public void contextLoads() {
	}

}

