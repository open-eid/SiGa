package ee.openeid.siga.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_BUILD_TIME;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_NAME;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_VERSION;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.NOT_AVAILABLE;

@Component
@Slf4j
public class MetaInfoHealthIndicator implements HealthIndicator {

    protected static final String RESPONSE_PARAM_WEBAPP_NAME = "webappName";
    protected static final String RESPONSE_PARAM_VERSION = "version";
    protected static final String RESPONSE_PARAM_BUILD_TIME = "buildTime";
    protected static final String RESPONSE_PARAM_START_TIME = "startTime";
    protected static final String RESPONSE_PARAM_CURRENT_TIME = "currentTime";
    protected static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    protected static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);

    private final ZonedDateTime instanceStarted;
    private final ZonedDateTime built;
    private final String name;
    private final String version;

    @Autowired
    public MetaInfoHealthIndicator(ManifestReader manifestReader) {
        instanceStarted = Instant.now().atZone(ZoneOffset.UTC);
        built = getInstanceBuilt(manifestReader);
        name = manifestReader.read(MANIFEST_PARAM_NAME);
        version = manifestReader.read(MANIFEST_PARAM_VERSION);
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail(RESPONSE_PARAM_WEBAPP_NAME, formatValue(name))
                .withDetail(RESPONSE_PARAM_VERSION, formatValue(version))
                .withDetail(RESPONSE_PARAM_BUILD_TIME, formatValue(getFormattedTime(built)))
                .withDetail(RESPONSE_PARAM_START_TIME, formatValue(getFormattedTime(instanceStarted)))
                .withDetail(RESPONSE_PARAM_CURRENT_TIME, formatValue(getFormattedTime(Instant.now().atZone(ZoneOffset.UTC))))
                .build();
    }

    private static Object formatValue(final String value) {
        return value == null ? NOT_AVAILABLE : value;
    }

    private static String getFormattedTime(ZonedDateTime zonedDateTime) {
        return (zonedDateTime != null)
                ? zonedDateTime.format(DEFAULT_DATE_TIME_FORMATTER)
                : null;
    }

    private static ZonedDateTime getInstanceBuilt(ManifestReader manifestReader) {
        String buildTime = manifestReader.read(MANIFEST_PARAM_BUILD_TIME);

        if (buildTime == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(buildTime, DEFAULT_DATE_TIME_FORMATTER).atZone(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            log.error("Could not parse the build time! ", e);
            return null;
        }
    }

}
