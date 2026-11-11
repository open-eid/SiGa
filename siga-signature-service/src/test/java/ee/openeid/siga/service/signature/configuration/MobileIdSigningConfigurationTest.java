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
    void shouldNotCreateMobileIdBeans_WhenMobileIdProfileIsInactive() {
        // No siga.midrest.* properties at all: with the profile off nothing may bind or validate them.
        contextRunner("smartId", "datafileContainer").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MobileIdApiClient.class);
            assertThat(context).doesNotHaveBean(MobileIdClientConfigurationProperties.class);
            assertThat(context).doesNotHaveBean(MobileIdSigningDelegate.class);
        });
    }

    @Test
    void shouldCreateOnlyHashcodeDelegate_WhenDatafileContainerProfileIsInactive() {
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
    void shouldCreateOneDelegatePerContainerType_WhenDatafileContainerProfileIsActive() {
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
    void shouldBindDefaults_WhenOnlyRequiredPropertiesAreSet() {
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
    void shouldBindOverrides_WhenOptionalPropertiesAreSet() {
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
    void shouldFailStartup_WhenRequiredPropertyIsMissing(String missingProperty, String expectedMessage) {
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
    void shouldFailStartup_WhenRequiredStringIsBlank(String blankProperty) {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(blankProperty)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "siga.midrest.connect-timeout=0ms",
            "siga.midrest.long-polling-timeout=0ms",
            "siga.midrest.status-polling-delay=-1ms"
    })
    void shouldFailStartup_WhenDurationIsBelowMinimum(String invalidProperty) {
        contextRunner("mobileId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(invalidProperty)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("Binding validation errors on siga.midrest");
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
