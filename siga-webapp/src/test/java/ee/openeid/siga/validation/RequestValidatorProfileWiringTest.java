package ee.openeid.siga.validation;

import ch.qos.logback.classic.Level;
import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.StringUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mobileId and smartId are independent profiles. Each one alone decides whether its request validator exists
 */
class RequestValidatorProfileWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "siga.midrest.url=https://mid.example.test/mid-api",
                    "siga.midrest.truststore-path=classpath:mid.truststore.p12",
                    "siga.midrest.truststore-password=changeit",
                    "siga.sid.url=https://sid.example.test/smart-id-rp/v2/",
                    "siga.sid.truststore-path=classpath:sid.truststore.p12",
                    "siga.sid.truststore-password=changeit")
            .withUserConfiguration(MobileIdApiClient.class, SmartIdApiClient.class, RequestValidator.class,
                    MobileIdRequestValidator.class, SmartIdRequestValidator.class);

    @ParameterizedTest(name = "active profiles: [{0}]")
    @CsvSource({
            "'mobileId,smartId', true,  true",
            "mobileId,           true,  false",
            "smartId,            false, true",
            "'',                 false, false"
    })
    void contextStartup_WhenProfileCombination_MatchingValidatorsCreatedAndEnabledLogged(
            String profiles, boolean mobileIdEnabled, boolean smartIdEnabled) {
        try (LogCapture logs = LogCapture.of(RequestValidator.class.getPackageName())) {
            contextRunner
                    .withInitializer(context -> {
                        context.getEnvironment().setActiveProfiles(StringUtils.commaDelimitedListToStringArray(profiles));
                        // registerSingleton: as a bean definition it would be bound and validated (hmac/jasypt required).
                        context.getBeanFactory().registerSingleton("securityConfigurationProperties", new SecurityConfigurationProperties());
                    })
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(RequestValidator.class);
                        assertThat(context.getBeansOfType(MobileIdRequestValidator.class)).hasSize(mobileIdEnabled ? 1 : 0);
                        assertThat(context.getBeansOfType(SmartIdRequestValidator.class)).hasSize(smartIdEnabled ? 1 : 0);
                    });

            List<String> infoMessages = logs.messages(Level.INFO);
            assertThat(infoMessages).hasSize((mobileIdEnabled ? 1 : 0) + (smartIdEnabled ? 1 : 0));
            assertThat(infoMessages.stream().anyMatch(message -> message.startsWith("Mobile-ID signing enabled"))).isEqualTo(mobileIdEnabled);
            assertThat(infoMessages.stream().anyMatch(message -> message.startsWith("Smart-ID signing enabled"))).isEqualTo(smartIdEnabled);
        }
    }
}
