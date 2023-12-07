package ee.openeid.siga.monitoring;

import com.jcabi.manifests.Manifests;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ManifestReader {

    public String read(final String parameterName) {
        try {
            return Manifests.read(parameterName);
        } catch (Exception e) {
            log.warn("Failed to fetch parameter '" + parameterName + "' from manifest file! Either you are not running the application as a jar/war package or there is a problem with the build configuration. " + e.getMessage());
            return null;
        }
    }
}
