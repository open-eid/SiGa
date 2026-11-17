package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.container.MobileIdSigningDelegate;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the {@code mobileId} profile wiring: the profile alone decides whether the Mobile-ID client,
 * its {@code siga.midrest.*} properties and the signing delegates exist, and the properties are
 * validated only when the profile is active.
 */
class MobileIdSigningConfigurationTest {

    private static final String[] VALID_PROPERTIES = {
            "siga.midrest.url=https://mid.example.test/mid-api",
            "siga.midrest.truststore-path=classpath:mid_truststore.p12",
            "siga.midrest.truststore-password=changeIt"
    };

    private final HashcodeContainerSigningService hashcodeSigningService = mock(HashcodeContainerSigningService.class);
    private final AsicContainerSigningService asicSigningService = mock(AsicContainerSigningService.class);

    @Test
    void contextStartup_WhenMobileIdProfileInactive_NoMobileIdBeansCreated() {
        // No siga.midrest.* properties at all: with the profile off nothing may bind or validate them.
        contextRunner("smartId", "datafileContainer").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MobileIdApiClient.class);
            assertThat(context).doesNotHaveBean(MobileIdClientConfigurationProperties.class);
            assertThat(context).doesNotHaveBean(MobileIdSigningDelegate.class);
        });
    }

    @Test
    void contextStartup_WhenDatafileContainerProfileInactive_OnlyHashcodeDelegateCreated() {
        // The ASiC signing service bean is present on purpose: the profile, not a missing dependency,
        // must keep the ASiC delegate out.
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MobileIdApiClient.class);
                    assertThat(context).hasSingleBean(MobileIdSigningDelegate.class);
                    MobileIdSigningDelegate<?> delegate = context.getBean(MobileIdSigningDelegate.class);
                    assertThat(delegate.getContainerSigningService()).isSameAs(hashcodeSigningService);
                });
    }

    @Test
    void contextStartup_WhenDatafileContainerProfileActive_DelegatePerContainerTypeCreated() {
        contextRunner("mobileId", "datafileContainer")
                .withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(MobileIdSigningDelegate.class)).hasSize(2);
                    assertThat(delegateFor(context, HashcodeContainerSigningService.class).getContainerSigningService())
                            .isSameAs(hashcodeSigningService);
                    assertThat(delegateFor(context, AsicContainerSigningService.class).getContainerSigningService())
                            .isSameAs(asicSigningService);
                });
    }

    @Test
    void propertyBinding_WhenOnlyRequiredPropertiesSet_DefaultsApplied() {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    MobileIdClientConfigurationProperties properties = context.getBean(MobileIdClientConfigurationProperties.class);
                    assertThat(properties.url()).isEqualTo("https://mid.example.test/mid-api");
                    assertThat(properties.truststorePath().exists()).isTrue();
                    assertThat(properties.truststorePassword()).isEqualTo("changeIt");
                    assertThat(properties.allowedCountries()).containsExactly("EE", "LT");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(5000));
                    assertThat(properties.longPollingTimeout()).isEqualTo(Duration.ofMillis(30000));
                    assertThat(properties.statusPollingDelay()).isEqualTo(Duration.ofMillis(6000));
                });
    }

    @Test
    void propertyBinding_WhenOptionalPropertiesSet_OverridesApplied() {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(
                        "siga.midrest.allowed-countries=EE",
                        "siga.midrest.connect-timeout=2s",
                        "siga.midrest.long-polling-timeout=10s",
                        "siga.midrest.status-polling-delay=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MobileIdClientConfigurationProperties properties = context.getBean(MobileIdClientConfigurationProperties.class);
                    assertThat(properties.allowedCountries()).containsExactly("EE");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.longPollingTimeout()).isEqualTo(Duration.ofSeconds(10));
                    // A bare 0 is used by SessionStatusReprocessingServiceTest and must stay valid.
                    assertThat(properties.statusPollingDelay()).isZero();
                });
    }

    @ParameterizedTest(name = "missing {0}")
    @CsvSource({
            "url,                 siga.midrest.url property must be set",
            "truststore-path,     siga.midrest.truststorePath property must be set",
            "truststore-password, siga.midrest.truststorePassword property must be set"
    })
    void propertyValidation_WhenRequiredPropertyMissing_StartupFails(String missingProperty, String expectedMessage) {
        contextRunner("mobileId")
                .withPropertyValues(validPropertiesWithout(missingProperty))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("Binding validation errors on siga.midrest")
                            .hasStackTraceContaining(expectedMessage);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"siga.midrest.url=", "siga.midrest.truststore-password=  "})
    void propertyValidation_WhenRequiredStringBlank_StartupFails(String blankProperty) {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(blankProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void propertyValidation_WhenAllowedCountriesEmpty_StartupFails() {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("siga.midrest.allowed-countries=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("siga.midrest.allowedCountries must not be empty");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"siga.midrest.truststore-path=classpath:missing.p12", "siga.midrest.truststore-password=wrong"})
    void contextStartup_WhenTruststoreUnreadable_StartupFails(String invalidProperty) {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(invalidProperty)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().hasStackTraceContaining("ResourceUtil.loadPkcs12KeyStore");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "siga.midrest.connect-timeout=0ms",
            "siga.midrest.long-polling-timeout=0ms",
            "siga.midrest.long-polling-timeout=999ms",
            "siga.midrest.status-polling-delay=-1ms"
    })
    void propertyValidation_WhenDurationBelowMinimum_StartupFails(String invalidProperty) {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(invalidProperty)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("Binding validation errors on siga.midrest");
                });
    }

    @Test
    void propertyValidation_WhenUrlInvalid_StartupFails() {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("siga.midrest.url=mid.example.test/mid-api")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("Binding validation errors on siga.midrest")
                            .hasStackTraceContaining("siga.midrest.url must be a valid URL");
                });
    }

    private ApplicationContextRunner contextRunner(String... activeProfiles) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles(activeProfiles);
                    // registerSingleton, not withBean: ContainerSigningService has @Autowired setters that
                    // Spring would otherwise try to satisfy on the mocks.
                    context.getBeanFactory().registerSingleton("hashcodeContainerSigningService", hashcodeSigningService);
                    context.getBeanFactory().registerSingleton("asicContainerSigningService", asicSigningService);
                })
                .withUserConfiguration(MobileIdApiClient.class, MobileIdSigningConfiguration.class);
    }

    private static MobileIdSigningDelegate<?> delegateFor(ApplicationContext context, Class<?> signingServiceType) {
        ResolvableType type = ResolvableType.forClassWithGenerics(MobileIdSigningDelegate.class, signingServiceType);
        return (MobileIdSigningDelegate<?>) context.getBeanProvider(type).getObject();
    }

    private static String[] validPropertiesWithout(String property) {
        return Arrays.stream(VALID_PROPERTIES)
                .filter(entry -> !entry.startsWith("siga.midrest." + property + "="))
                .toArray(String[]::new);
    }
}
