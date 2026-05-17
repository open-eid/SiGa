package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStorage;
import org.apache.ignite.Ignite;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link IgniteSessionConfiguration} is required: it's the only place where the Ignite
 * variant of every session-storage bean is wired, gated at the class level on
 * {@code siga.session-storage.type=ignite}. Without this configuration class no Ignite bean ever
 * appears in the context; with it, the Ignite SPI implementations plus the expiry notifier all
 * materialise together.
 *
 * <p>A deep-stub mock {@link Ignite} stands in for the production node so
 * {@link IgniteSessionExpiryNotifier}'s {@code @PostConstruct} finds non-null {@code cluster()} and
 * {@code events()} chains during the bean-graph check. Real listener behaviour is covered by
 * {@code IgniteSessionExpiryNotifierTest}, which runs against an embedded Ignite.
 */
class IgniteSessionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IgniteSessionConfiguration.class)
            .withPropertyValues(
                    "siga.session-storage.application-cache-version=v1",
                    "siga.session-storage.ignite.configuration-location=classpath:ignite-test-configuration.xml")
            .withBean(Ignite.class, () -> Mockito.mock(Ignite.class, Mockito.RETURNS_DEEP_STUBS));

    @Test
    void shouldWireAllIgniteBeans_WhenSessionStorageTypeIsIgnite() {
        contextRunner
                .withPropertyValues("siga.session-storage.type=ignite")
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionStorage.class);
                    assertThat(context.getBean(SessionStorage.class))
                            .isInstanceOf(IgniteSessionStorage.class);
                    assertThat(context).hasSingleBean(SessionLockRegistry.class);
                    assertThat(context.getBean(SessionLockRegistry.class))
                            .isInstanceOf(IgniteSessionLockRegistry.class);
                    assertThat(context).hasSingleBean(SessionStatusScanner.class);
                    assertThat(context.getBean(SessionStatusScanner.class))
                            .isInstanceOf(IgniteSessionStatusScanner.class);
                    assertThat(context).hasSingleBean(IgniteSessionExpiryNotifier.class);
                });
    }

    @Test
    void shouldNotWireAnyIgniteBeans_WhenSessionStorageTypeIsRedis() {
        contextRunner
                .withPropertyValues("siga.session-storage.type=redis")
                .run(context -> {
                    // The whole @Configuration is gated by havingValue=ignite — for type=redis
                    // the class is skipped entirely.
                    assertThat(context).doesNotHaveBean(IgniteSessionStorage.class);
                    assertThat(context).doesNotHaveBean(IgniteSessionLockRegistry.class);
                    assertThat(context).doesNotHaveBean(IgniteSessionStatusScanner.class);
                    assertThat(context).doesNotHaveBean(IgniteSessionExpiryNotifier.class);
                });
    }

    @Test
    void shouldNotWireAnyIgniteBeans_WhenSessionStorageTypeIsUnset() {
        // havingValue=ignite has no matchIfMissing fallback, so an unset type means the Ignite
        // configuration stays inert. Pins that Redis is the implicit default.
        contextRunner
                .withSystemProperties("siga.session-storage.type=")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(IgniteSessionStorage.class);
                    assertThat(context).doesNotHaveBean(IgniteSessionLockRegistry.class);
                    assertThat(context).doesNotHaveBean(IgniteSessionStatusScanner.class);
                });
    }
}
