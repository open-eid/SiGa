package ee.openeid.siga.session.ignite;

import ee.openeid.siga.common.session.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors {@link SignatureStatusRequestFilterTest} for the certificate path. The two filters are
 * intentionally siblings rather than a shared abstraction (peer class loading is fragile to
 * inheritance hierarchies), so we exercise the same boolean cases against the cert variant to
 * catch any future drift between them.
 */
class CertificateStatusRequestFilterTest {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration EXCEPTION_TIMEOUT = Duration.ofMinutes(10);
    private static final long MAX_RETRIES = 3L;

    private static final Instant AGED_PROCESSING = Instant.now().minus(Duration.ofMinutes(10));
    private static final Instant FRESH = Instant.now().plus(Duration.ofMinutes(1));
    private static final Instant AGED_EXCEPTION = Instant.now().minus(Duration.ofMinutes(15));

    private static Stream<Arguments> filterCases() {
        return Stream.of(
                Arguments.of("PROCESSING aged past cutoff, retries ok",
                        ProcessingStatus.PROCESSING, AGED_PROCESSING, 0, true),
                Arguments.of("PROCESSING within cutoff window",
                        ProcessingStatus.PROCESSING, FRESH, 0, false),
                Arguments.of("EXCEPTION aged past cutoff, retries ok",
                        ProcessingStatus.EXCEPTION, AGED_EXCEPTION, 0, true),
                Arguments.of("EXCEPTION within cutoff window",
                        ProcessingStatus.EXCEPTION, FRESH, 0, false),
                Arguments.of("RESULT regardless of age",
                        ProcessingStatus.RESULT, AGED_PROCESSING, 0, false),
                Arguments.of("PROCESSING aged but retries over the ceiling",
                        ProcessingStatus.PROCESSING, AGED_PROCESSING, (int) (MAX_RETRIES + 1), false),
                Arguments.of("EXCEPTION aged but retries over the ceiling",
                        ProcessingStatus.EXCEPTION, AGED_EXCEPTION, (int) (MAX_RETRIES + 1), false),
                Arguments.of("PROCESSING aged at the retry ceiling exactly",
                        ProcessingStatus.PROCESSING, AGED_PROCESSING, (int) MAX_RETRIES, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filterCases")
    void shouldEvaluatePredicate_PerStatusAgeAndRetryCounter(@SuppressWarnings("unused") String label,
                                                             ProcessingStatus status,
                                                             Instant timestamp,
                                                             int processingCounter,
                                                             boolean expected) {
        CertificateStatusRequestFilter filter = new CertificateStatusRequestFilter(
                MAX_RETRIES, PROCESSING_TIMEOUT, EXCEPTION_TIMEOUT);

        assertEquals(expected,
                CertificateStatusRequestFilter.isApplyFilter(filter, status, timestamp, processingCounter));
    }

    @Test
    void shouldNotMatch_WhenTimestampWithinCutoffWindow() {
        // Mirrors SignatureStatusRequestFilterTest: probe at now().minusSeconds(timeoutSeconds - 1)
        // sits ~1s inside the constructor-captured cutoff. Parameterised AGED_/FRESH cases above
        // cover the past side of the isBefore boundary.
        long timeoutSeconds = PROCESSING_TIMEOUT.toSeconds();
        CertificateStatusRequestFilter filter = new CertificateStatusRequestFilter(
                MAX_RETRIES, PROCESSING_TIMEOUT, EXCEPTION_TIMEOUT);
        Instant justInsideCutoff = Instant.now().minusSeconds(timeoutSeconds - 1);

        assertFalse(CertificateStatusRequestFilter.isApplyFilter(
                filter, ProcessingStatus.PROCESSING, justInsideCutoff, 0));
    }

    @Test
    void shouldBeSerializable_ForPeerClassLoading() throws IOException {
        CertificateStatusRequestFilter filter = new CertificateStatusRequestFilter(
                MAX_RETRIES, PROCESSING_TIMEOUT, EXCEPTION_TIMEOUT);
        assertTrue(filter instanceof Serializable);

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(filter);
            assertNotNull(bytes.toByteArray());
        }
    }
}
