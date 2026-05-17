package ee.openeid.siga.session.ignite;

import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.SessionStatusScannerTest;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ignite-backed contract tests for {@link IgniteSessionStatusScanner} plus backend-specific
 * assertions. The shared SPI contract is inherited from {@link SessionStatusScannerTest}; seeding
 * goes through {@link IgniteSessionStorage#update} because the scanner reads from the
 * {@code SIGNATURE_SESSION} / {@code CERTIFICATE_SESSION} caches that {@code update} populates.
 */
class IgniteSessionStatusScannerTest extends SessionStatusScannerTest {

    private static Ignite ignite;
    private static IgniteSessionStorage storage;
    private static IgniteSessionStatusScanner scanner;

    @BeforeAll
    static void startIgnite() {
        System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
        ignite = Ignition.start("ignite-test-configuration.xml");
        storage = new IgniteSessionStorage(ignite);
        scanner = new IgniteSessionStatusScanner(ignite);
    }

    @AfterAll
    static void stopIgnite() {
        if (ignite != null) {
            ignite.close();
        }
    }

    @Override
    protected SessionStatusScanner scanner() {
        return scanner;
    }

    @Override
    protected void seedSession(Session session) {
        storage.update(session);
    }

    @Override
    protected void resetState() {
        ignite.cache(CacheName.CONTAINER_SESSION.name()).clear();
        ignite.cache(CacheName.SIGNATURE_SESSION.name()).clear();
        ignite.cache(CacheName.CERTIFICATE_SESSION.name()).clear();
    }

    @Test
    void shouldNotEmit_WhenProcessingCounterExceedsMaxRetries() {
        // Backend-specific optimisation: the Ignite filter (SignatureStatusRequestFilter /
        // CertificateStatusRequestFilter) enforces processingCounter <= maxProcessingRetries
        // server-side via peer-loaded predicates, so over-the-limit sessions never reach the
        // consumer. The SPI contract permits but doesn't require this.
        seedSession(buildSession("v1_svc_over_retry",
                Map.of("sig-1", signatureSession(ProcessingStatus.PROCESSING,
                        Instant.now().minus(Duration.ofMinutes(10)), 5)),
                Map.of()));

        List<String> emitted = new ArrayList<>();
        scanner.scanSignatureSessions(
                new StatusReprocessingFilter(1L, Instant.now(), Instant.now()),
                emitted::add);

        assertEquals(List.of(), emitted,
                "Ignite scanner should drop sessions with processingCounter > maxProcessingRetries server-side");
    }
}
