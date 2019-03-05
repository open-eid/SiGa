package ee.openeid.siga;

import ee.openeid.siga.auth.SecurityConfiguration;
import ee.openeid.siga.auth.properties.SigaVaultProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@TestPropertySource(locations={"classpath:application-test.properties"})
@SpringBootTest(classes = SigaApplicationTests.TestConfiguration.class, webEnvironment=RANDOM_PORT, properties = {"spring.main" +
		".allow-bean-definition-overriding=true"})
@ActiveProfiles("test")
public class SigaApplicationTests {

	@Test
	public void contextLoads() {
	}

	@Profile("test")
	@Configuration
	@Import(SigaApplication.class)
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

