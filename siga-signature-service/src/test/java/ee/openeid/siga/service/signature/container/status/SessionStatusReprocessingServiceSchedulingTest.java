package ee.openeid.siga.service.signature.container.status;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code @Scheduled} wiring on {@link SessionStatusReprocessingService}. This is the
 * scheduler that drives the reprocessing cycle, so a refactor that drops the annotation, changes
 * the fixed-rate property key, or renames the method must surface here.
 */
class SessionStatusReprocessingServiceSchedulingTest {

    private static final String EXPECTED_FIXED_RATE = "${siga.status-reprocessing.fixed-rate:5000}";
    private static final String EXPECTED_INITIAL_DELAY = "${siga.status-reprocessing.initial-delay:5000}";

    @Test
    void signatureReprocessingMethodIsScheduledWithReprocessingProperties() {
        Scheduled scheduled = scheduledAnnotationOn("processFailedStatusRequests");
        assertEquals(EXPECTED_FIXED_RATE, scheduled.fixedRateString());
        assertEquals(EXPECTED_INITIAL_DELAY, scheduled.initialDelayString());
    }

    @Test
    void certificateReprocessingMethodIsScheduledWithReprocessingProperties() {
        Scheduled scheduled = scheduledAnnotationOn("processFailedCertificateStatusRequests");
        assertEquals(EXPECTED_FIXED_RATE, scheduled.fixedRateString());
        assertEquals(EXPECTED_INITIAL_DELAY, scheduled.initialDelayString());
    }

    @Test
    void exactlyTwoMethodsAreScheduled() {
        List<String> scheduledMethods = Arrays.stream(SessionStatusReprocessingService.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .map(Method::getName)
                .sorted()
                .toList();

        assertEquals(2, scheduledMethods.size(),
                "Expected exactly two @Scheduled methods (signature + certificate) but got " + scheduledMethods);
        assertTrue(scheduledMethods.contains("processFailedStatusRequests"));
        assertTrue(scheduledMethods.contains("processFailedCertificateStatusRequests"));
    }

    private static Scheduled scheduledAnnotationOn(String methodName) {
        Method method = Arrays.stream(SessionStatusReprocessingService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + methodName));
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, methodName + " is missing @Scheduled");
        return scheduled;
    }
}
