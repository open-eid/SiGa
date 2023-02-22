package ee.openeid.siga.test.helper;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

@RequiredArgsConstructor
public class AssumingProfileActive {

    private static Properties properties;

    private final String profile;

    static {
        properties = new Properties();
        ClassLoader classLoader = AssumingProfileActive.class.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream("application-test.properties")) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application-test.properties", e);
        }
    }

    private static boolean isProfileActive(final String profile) {
        return Arrays.stream(properties.getProperty("siga.profiles.active", "").split(","))
                .anyMatch(profile::equals);

    }

}
