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
 * Pins the per-entry predicate logic for {@link SignatureStatusRequestFilter}. The class is loaded
 * into Ignite server nodes via peer class loading; the {@code IgniteSessionStatusScanner} tests
 * already cover the BinaryObject extraction end-to-end, so this suite focuses on the static
 * {@code isApplyFilter} that encodes the boolean semantics. A mutation here (status flip, cutoff
 * direction, retry comparator) would let stale or terminal signatures through to the reprocessor.
 */
class SignatureStatusRequestFilterTest {

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
        SignatureStatusRequestFilter filter = new SignatureStatusRequestFilter(
                MAX_RETRIES, PROCESSING_TIMEOUT, EXCEPTION_TIMEOUT);

        assertEquals(expected,
                SignatureStatusRequestFilter.isApplyFilter(filter, status, timestamp, processingCounter));
    }

    @Test
    void shouldNotMatch_WhenTimestampWithinCutoffWindow() {
        // The filter captures its cutoff in the constructor, so probing with
        // now().minusSeconds(timeoutSeconds - 1) lands ~1s newer than that cutoff (and never older,
        // because the test runs after construction). That's enough to pin the in-window side of the
        // isBefore boundary; the parameterised AGED_/FRESH cases already cover the symmetric past
        // side, so together they catch an isBefore -> !isAfter mutation.
        long timeoutSeconds = PROCESSING_TIMEOUT.toSeconds();
        SignatureStatusRequestFilter filter = new SignatureStatusRequestFilter(
                MAX_RETRIES, PROCESSING_TIMEOUT, EXCEPTION_TIMEOUT);
        Instant justInsideCutoff = Instant.now().minusSeconds(timeoutSeconds - 1);

        assertFalse(SignatureStatusRequestFilter.isApplyFilter(
                        filter, ProcessingStatus.PROCESSING, justInsideCutoff, 0),
                "Timestamps newer than the cutoff (still inside the window) must not match");
    }

    @Test
    void shouldBeSerializable_ForPeerClassLoading() throws IOException {
        SignatureStatusRequestFilter filter = new SignatureStatusRequestFilter(
                MAX_RETRIES, PROCESSING_TIMEOUT, EXCEPTION_TIMEOUT);
        assertTrue(filter instanceof Serializable, "Filter ships to server nodes; must be Serializable");

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(filter);
            assertNotNull(bytes.toByteArray());
        }
    }
}
