package ee.openeid.siga.monitoring;

import com.jcabi.manifests.Manifests;
import com.jcabi.manifests.ServletMfs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContext;

@Component
@Slf4j
public class ManifestReader {

    @Autowired
    public ManifestReader(final ServletContext servletContext) {
        try {
            Manifests.DEFAULT.append(new ServletMfs(servletContext));
        } catch (Exception e) {
            log.error("Failed to set up " + e.getMessage(), e);
        }
    }

    public String read(final String parameterName) {
        try {
            return Manifests.read(parameterName);
        } catch (Exception e) {
            log.warn("Failed to fetch parameter '" + parameterName + "' from manifest file! Either you are not running the application as a jar/war package or there is a problem with the build configuration. " + e.getMessage());
            return null;
        }
    }
}
