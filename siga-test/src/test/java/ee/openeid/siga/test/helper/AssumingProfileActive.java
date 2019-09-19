package ee.openeid.siga.test.helper;

import lombok.RequiredArgsConstructor;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

@RequiredArgsConstructor
public class AssumingProfileActive implements TestRule {

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

    @Override
    public Statement apply(final Statement statement, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (!isProfileActive(profile)) {
                    throw new AssumptionViolatedException("\"" + profile + "\" profile not active");
                } else {
                    statement.evaluate();
                }
            }
        };
    }

}
