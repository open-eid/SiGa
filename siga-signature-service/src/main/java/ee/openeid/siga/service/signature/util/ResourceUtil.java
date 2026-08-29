package ee.openeid.siga.service.signature.util;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public class ResourceUtil {

    private ResourceUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static void loadResourceTo(Resource resource, Consumer<InputStream> consumer) {
        try (InputStream inputStream = resource.getInputStream()) {
            consumer.accept(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open " + resource, e);
        }
    }

}
