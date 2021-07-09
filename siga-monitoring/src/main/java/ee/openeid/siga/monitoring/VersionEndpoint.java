package ee.openeid.siga.monitoring;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_VERSION;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.NOT_AVAILABLE;

@Component
@Endpoint(id = "version", enableByDefault = false)
public class VersionEndpoint {

    public static final String RESPONSE_PARAM_VERSION = "version";

    private final String version;

    public VersionEndpoint(ManifestReader manifestReader) {
        String versionFromManifest = manifestReader.read(MANIFEST_PARAM_VERSION);
        version = (versionFromManifest != null) ? versionFromManifest : NOT_AVAILABLE;
    }

    @ReadOperation
    public Map<String, Object> version() {
        return Map.of(RESPONSE_PARAM_VERSION, version);
    }

}
