package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.container.SmartIdSigningDelegate;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdInteractionType;
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
 * Pins the {@code smartId} profile wiring: the profile alone decides whether the Smart-ID client,
 * its {@code siga.sid.*} properties and the signing delegates exist, and the properties are
 * validated only when the profile is active.
 */
class SmartIdSigningConfigurationTest {

    private static final String[] VALID_PROPERTIES = {
            "siga.sid.url=https://sid.example.test/smart-id-rp/v2/",
            "siga.sid.truststore-path=classpath:sid_truststore.p12",
            "siga.sid.truststore-password=changeIt"
    };

    private final HashcodeContainerSigningService hashcodeSigningService = mock(HashcodeContainerSigningService.class);
    private final AsicContainerSigningService asicSigningService = mock(AsicContainerSigningService.class);

    @Test
    void shouldNotCreateSmartIdBeans_WhenSmartIdProfileIsInactive() {
        // No siga.sid.* properties at all: with the profile off nothing may bind or validate them.
        contextRunner("mobileId", "datafileContainer").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SmartIdApiClient.class);
            assertThat(context).doesNotHaveBean(SmartIdClientConfigurationProperties.class);
            assertThat(context).doesNotHaveBean(SmartIdSigningDelegate.class);
        });
    }

    @Test
    void shouldCreateOnlyHashcodeDelegate_WhenDatafileContainerProfileIsInactive() {
        // The ASiC signing service bean is present on purpose: the profile, not a missing dependency,
        // must keep the ASiC delegate out.
        contextRunner("smartId")
                .withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SmartIdApiClient.class);
                    assertThat(context).hasSingleBean(SmartIdSigningDelegate.class);
                    SmartIdSigningDelegate<?> delegate = context.getBean(SmartIdSigningDelegate.class);
                    assertThat(delegate.getContainerSigningService()).isSameAs(hashcodeSigningService);
                });
    }

    @Test
    void shouldCreateOneDelegatePerContainerType_WhenDatafileContainerProfileIsActive() {
        contextRunner("smartId", "datafileContainer")
                .withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(SmartIdSigningDelegate.class)).hasSize(2);
                    assertThat(delegateFor(context, HashcodeContainerSigningService.class).getContainerSigningService())
                            .isSameAs(hashcodeSigningService);
                    assertThat(delegateFor(context, AsicContainerSigningService.class).getContainerSigningService())
                            .isSameAs(asicSigningService);
                });
    }

    @Test
    void shouldBindDefaults_WhenOnlyRequiredPropertiesAreSet() {
        contextRunner("smartId")
                .withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    SmartIdClientConfigurationProperties properties = context.getBean(SmartIdClientConfigurationProperties.class);
                    assertThat(properties.url()).isEqualTo("https://sid.example.test/smart-id-rp/v2/");
                    assertThat(properties.truststorePath().exists()).isTrue();
                    assertThat(properties.truststorePassword()).isEqualTo("changeIt");
                    assertThat(properties.allowedCountries()).containsExactly("EE", "LT", "LV");
                    assertThat(properties.interactionType()).isEqualTo(SmartIdInteractionType.DISPLAY_TEXT_AND_PIN);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(5000));
                    assertThat(properties.sessionStatusResponseSocketOpenTime()).isEqualTo(Duration.ofMillis(30000));
                    assertThat(properties.statusPollingDelay()).isEqualTo(Duration.ofMillis(6000));
                });
    }

    @Test
    void shouldBindOverrides_WhenOptionalPropertiesAreSet() {
        // Mirrors the shapes used in siga-webapp application-test.yml: camelCase key and a bare number of millis.
        contextRunner("smartId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(
                        "siga.sid.allowedCountries=EE, LT",
                        "siga.sid.interaction-type=VERIFICATION_CODE_CHOICE",
                        "siga.sid.connect-timeout=2s",
                        "siga.sid.session-status-response-socket-open-time=1000",
                        "siga.sid.status-polling-delay=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SmartIdClientConfigurationProperties properties = context.getBean(SmartIdClientConfigurationProperties.class);
                    assertThat(properties.allowedCountries()).containsExactly("EE", "LT");
                    assertThat(properties.interactionType()).isEqualTo(SmartIdInteractionType.VERIFICATION_CODE_CHOICE);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.sessionStatusResponseSocketOpenTime()).isEqualTo(Duration.ofMillis(1000));
                    assertThat(properties.statusPollingDelay()).isZero();
                });
    }

    @ParameterizedTest(name = "missing {0}")
    @CsvSource({
            "url,                 siga.sid.url property must be set",
            "truststore-path,     siga.sid.truststorePath property must be set",
            "truststore-password, siga.sid.truststorePassword property must be set"
    })
    void shouldFailStartup_WhenRequiredPropertyIsMissing(String missingProperty, String expectedMessage) {
        contextRunner("smartId")
                .withPropertyValues(validPropertiesWithout(missingProperty))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("Binding validation errors on siga.sid")
                            .hasStackTraceContaining(expectedMessage);
                });
    }

    @Test
    void shouldFailStartup_WhenInteractionTypeIsUnknown() {
        contextRunner("smartId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("siga.sid.interaction-type=NOT_AN_INTERACTION")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "siga.sid.connect-timeout=0ms",
            "siga.sid.session-status-response-socket-open-time=0ms",
            "siga.sid.status-polling-delay=-1ms"
    })
    void shouldFailStartup_WhenDurationIsBelowMinimum(String invalidProperty) {
        contextRunner("smartId")
                .withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues(invalidProperty)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("Binding validation errors on siga.sid");
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
                .withUserConfiguration(SmartIdApiClient.class, SmartIdSigningConfiguration.class);
    }

    private static SmartIdSigningDelegate<?> delegateFor(ApplicationContext context, Class<?> signingServiceType) {
        ResolvableType type = ResolvableType.forClassWithGenerics(SmartIdSigningDelegate.class, signingServiceType);
        return (SmartIdSigningDelegate<?>) context.getBeanProvider(type).getObject();
    }

    private static String[] validPropertiesWithout(String property) {
        return Arrays.stream(VALID_PROPERTIES)
                .filter(entry -> !entry.startsWith("siga.sid." + property + "="))
                .toArray(String[]::new);
    }
}
