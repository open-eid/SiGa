package ee.openeid.siga.validation;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mobileId and smartId are documented as independent profiles, and RequestValidator exists in every
 * combination, so it must not require properties beans that only exist while their profile is active.
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
            .withUserConfiguration(MobileIdApiClient.class, SmartIdApiClient.class, RequestValidator.class);

    @ParameterizedTest(name = "active profiles: [{0}]")
    @ValueSource(strings = {"mobileId,smartId", "mobileId", "smartId", ""})
    void shouldCreateRequestValidator_InEveryMobileIdAndSmartIdProfileCombination(String profiles) {
        contextRunner
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles(StringUtils.commaDelimitedListToStringArray(profiles));
                    // registerSingleton: as a bean definition it would be bound and validated (hmac/jasypt required).
                    context.getBeanFactory().registerSingleton("securityConfigurationProperties", new SecurityConfigurationProperties());
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RequestValidator.class);
                });
    }
}
