package ee.openeid.siga.monitoring;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_BUILD_TIME;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_NAME;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_VERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
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
        healthIndicator = new MetaInfoHealthIndicator(manifestReader);
        Health health = healthIndicator.health();
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
        verifyCorrectStartAndServerTime(health);
    }

    @Test
    public void parametersFoundInManifestFile() {
        Mockito.when(manifestReader.read(MANIFEST_PARAM_NAME)).thenReturn(TEST_WEBAPP);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_VERSION)).thenReturn(TEST_VERSION);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_BUILD_TIME)).thenReturn(TEST_BUILD_TIME_IN_UTC);
        healthIndicator = new MetaInfoHealthIndicator(manifestReader);
        Health health = healthIndicator.health();
        assertEquals(Status.UP, health.getStatus());

        assertEquals(TEST_WEBAPP, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_WEBAPP_NAME));
        assertEquals(TEST_VERSION, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_VERSION));
        assertEquals(MetaInfoHealthIndicator.getFormattedTime(MetaInfoHealthIndicator.convertUtcToLocal(TEST_BUILD_TIME_IN_UTC)), health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_BUILD_TIME));
    }

    @Test
    public void invalidBuildTime() {
        Mockito.when(manifestReader.read(MANIFEST_PARAM_NAME)).thenReturn(TEST_WEBAPP);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_VERSION)).thenReturn(TEST_VERSION);
        Mockito.when(manifestReader.read(MANIFEST_PARAM_BUILD_TIME)).thenReturn("random_time");
        healthIndicator = new MetaInfoHealthIndicator(manifestReader);
        Health health = healthIndicator.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals(TEST_WEBAPP, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_WEBAPP_NAME));
        assertEquals(TEST_VERSION, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_VERSION));
        assertEquals(NOT_AVAILABLE, health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_BUILD_TIME));
    }

    private void verifyCorrectStartAndServerTime(Health health) {
        LocalDateTime startTime = parseDate(String.valueOf(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_START_TIME)));
        assertTimeDifferenceWithNowInSecondsIsLessThan(startTime, 10);
        LocalDateTime currentTime = parseDate(String.valueOf(health.getDetails().get(MetaInfoHealthIndicator.RESPONSE_PARAM_CURRENT_TIME)));
        assertTimeDifferenceWithNowInSecondsIsLessThan(currentTime, 10);
        assertTrue("Current time must be equal or greater to start time!", currentTime.compareTo(startTime) >= 0);
    }

    private LocalDateTime parseDate(String dateTimeText) {
        return LocalDateTime.parse(dateTimeText, DateTimeFormatter.ofPattern(MetaInfoHealthIndicator.DEFAULT_DATE_TIME_FORMAT));
    }

    private void assertTimeDifferenceWithNowInSecondsIsLessThan(LocalDateTime startDate, int seconds) {
        assertTrue(startDate.until(LocalDateTime.now(), ChronoUnit.SECONDS) < seconds);
    }
}
