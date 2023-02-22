package ee.openeid.siga.monitoring;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_BUILD_TIME;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_NAME;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_VERSION;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class MetaInfoHealthIndicatorTest {

    private static final String NOT_AVAILABLE = "N/A";
    private static final String TEST_WEBAPP = "TEST_WEBAPP";
    private static final String TEST_BUILD_TIME_IN_UTC = "2020-06-21T11:35:23Z";
    private static final String TEST_VERSION = "TEST_VERSION";


    private MetaInfoHealthIndicator healthIndicator;

    @Mock
    private ManifestReader manifestReader;

    @Test
    public void parametersMissingInManifestFile() {
        Instant earliestStartTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        healthIndicator = new MetaInfoHealthIndicator(manifestReader);
        Instant latestStartTime = Instant.now();

        Instant earliestCurrentTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Health health = healthIndicator.health();
        Instant latestCurrentTime = Instant.now();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(
                Stream.of(
                        MetaInfoHealthIndicator.RESPONSE_PARAM_WEBAPP_NAME,
                        MetaInfoHealthIndicator.RESPONSE_PARAM_VERSION,
                        MetaInfoHealthIndicator.RESPONSE_PARAM_BUILD_TIME,
                        MetaInfoHealthIndicator.RESPONSE_PARAM_START_TIME,
                        MetaInfoHealthIndicator.RESPONSE_PARAM_CURRENT_TIME
                ).collect(Collectors.toCollection(LinkedHashSet::new)), health.getDetails().keySet());
        assertEquals(NOT_AVAILABLE, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_WEBAPP_NAME));
        assertEquals(NOT_AVAILABLE, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_VERSION));
        assertEquals(NOT_AVAILABLE, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_BUILD_TIME));
        verifyTime(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_START_TIME), earliestStartTime, latestStartTime);
        verifyTime(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_CURRENT_TIME), earliestCurrentTime, latestCurrentTime);
    }

    @Test
    public void parametersFoundInManifestFile() {
        Mockito.when(manifestReader.read(MANIFEST_PARAM_NAME)).thenReturn(TEST_WEBAPP);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_VERSION)).thenReturn(TEST_VERSION);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_BUILD_TIME)).thenReturn(TEST_BUILD_TIME_IN_UTC);
        Instant earliestStartTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        healthIndicator = new MetaInfoHealthIndicator(manifestReader);
        Instant latestStartTime = Instant.now();

        Instant earliestCurrentTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Health health = healthIndicator.health();
        Instant latestCurrentTime = Instant.now();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(TEST_WEBAPP, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_WEBAPP_NAME));
        assertEquals(TEST_VERSION, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_VERSION));
        assertEquals(TEST_BUILD_TIME_IN_UTC, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_BUILD_TIME));
        verifyTime(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_START_TIME), earliestStartTime, latestStartTime);
        verifyTime(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_CURRENT_TIME), earliestCurrentTime, latestCurrentTime);
    }

    @Test
    public void invalidBuildTime() {
        Mockito.when(manifestReader.read(MANIFEST_PARAM_NAME)).thenReturn(TEST_WEBAPP);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_VERSION)).thenReturn(TEST_VERSION);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_BUILD_TIME)).thenReturn("random_time");
        Instant earliestStartTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        healthIndicator = new MetaInfoHealthIndicator(manifestReader);
        Instant latestStartTime = Instant.now();

        Instant earliestCurrentTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Health health = healthIndicator.health();
        Instant latestCurrentTime = Instant.now();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(TEST_WEBAPP, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_WEBAPP_NAME));
        assertEquals(TEST_VERSION, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_VERSION));
        assertEquals(NOT_AVAILABLE, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_BUILD_TIME));
        verifyTime(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_START_TIME), earliestStartTime, latestStartTime);
        verifyTime(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_CURRENT_TIME), earliestCurrentTime, latestCurrentTime);
    }

    private static void verifyTime(Object time, Instant earliest, Instant latest) {
        assertNotNull(time);
        Instant parsedTime = null;
        try {
            parsedTime = Instant.parse(time.toString());
        } catch (DateTimeParseException e) {
            fail("Failed to parse time: " + e.getMessage());
        }
        assertFalse(parsedTime.isBefore(earliest), String.format("Time (%s) must not be before %s", parsedTime, earliest));
        assertFalse(parsedTime.isAfter(latest), String.format("Time (%s) must not be after %s", parsedTime, latest));
    }

}
