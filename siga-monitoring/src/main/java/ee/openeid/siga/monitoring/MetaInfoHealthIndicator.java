package ee.openeid.siga.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@Slf4j
public class MetaInfoHealthIndicator implements HealthIndicator {

    protected static final String RESPONSE_PARAM_WEBAPP_NAME = "webappName";
    protected static final String RESPONSE_PARAM_VERSION = "version";
    protected static final String RESPONSE_PARAM_BUILD_TIME = "buildTime";
    protected static final String RESPONSE_PARAM_START_TIME = "startTime";
    protected static final String RESPONSE_PARAM_CURRENT_TIME = "currentTime";
    protected static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String NOT_AVAILABLE = "N/A";
    protected static final String MANIFEST_PARAM_NAME = "SiGa-Webapp-Name";
    protected static final String MANIFEST_PARAM_VERSION = "SiGa-Webapp-Version";
    protected static final String MANIFEST_PARAM_BUILD_TIME = "SiGa-Webapp-Build-Time";
    private ZonedDateTime instanceStarted = null;
    private ZonedDateTime built = null;
    private String name = null;
    private String version = null;

    private final ManifestReader manifestReader;

    @Autowired
    public MetaInfoHealthIndicator(ManifestReader manifestReader) {
        this.manifestReader = manifestReader;
        setIndicators();
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail(RESPONSE_PARAM_WEBAPP_NAME, formatValue(name))
                .withDetail(RESPONSE_PARAM_VERSION, formatValue(version))
                .withDetail(RESPONSE_PARAM_BUILD_TIME, formatValue(getFormattedTime(built)))
                .withDetail(RESPONSE_PARAM_START_TIME, formatValue(getFormattedTime(instanceStarted)))
                .withDetail(RESPONSE_PARAM_CURRENT_TIME, formatValue(getFormattedTime(ZonedDateTime.now())))
                .build();
    }

    private Object formatValue(final String value) {
        return value == null ? NOT_AVAILABLE : value;
    }

    protected static String getFormattedTime(final ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);
        return zonedDateTime.format(formatter);
    }

    private void setIndicators() {
        instanceStarted = ZonedDateTime.now();
        built = getInstanceBuilt();
        name = manifestReader.read(MANIFEST_PARAM_NAME);
        version = manifestReader.read(MANIFEST_PARAM_VERSION);
    }

    private ZonedDateTime getInstanceBuilt() {
        return convertUtcToLocal(manifestReader.read(MANIFEST_PARAM_BUILD_TIME));
    }

    protected static ZonedDateTime convertUtcToLocal(final String buildTime) {
        if (buildTime == null) {
            return null;
        }

        LocalDateTime date;
        try {
            date = LocalDateTime.parse(buildTime, DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT));
        } catch (DateTimeParseException e) {
            log.error("Could not parse the build time! ", e);
            return null;
        }
        ZonedDateTime dateTime = date.atZone(ZoneId.of("UTC"));
        return dateTime.withZoneSameInstant(ZoneId.systemDefault());
    }

}
