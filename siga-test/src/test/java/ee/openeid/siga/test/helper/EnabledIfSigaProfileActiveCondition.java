package ee.openeid.siga.test.helper;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class EnabledIfSigaProfileActiveCondition implements ExecutionCondition {

    private static final Properties PROPERTIES = new Properties();

    static {
        ClassLoader classLoader = EnabledIfSigaProfileActiveCondition.class.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream("application-test.properties")) {
            PROPERTIES.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application-test.properties", e);
        }
    }

    private static boolean isProfileActive(final String profile) {
        return Arrays
                .asList(PROPERTIES.getProperty("siga.profiles.active", "").split(","))
                .contains(profile);
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {
        return AnnotationUtils
                .findAnnotation(extensionContext.getElement(), EnabledIfSigaProfileActive.class)
                .map(EnabledIfSigaProfileActiveCondition::createConditionEvaluationResult)
                .orElseGet(() -> ConditionEvaluationResult.enabled(String.format(
                        "No annotation '%s' declared in extension context",
                        EnabledIfSigaProfileActive.class.getSimpleName()
                )));
    }

    private static ConditionEvaluationResult createConditionEvaluationResult(EnabledIfSigaProfileActive annotation) {
        final String[] profiles = annotation.value();
        if (ArrayUtils.isEmpty(profiles)) {
            return ConditionEvaluationResult.enabled("No active profiles required");
        }

        for (String profile : profiles) {
            if (!isProfileActive(profile)) {
                return ConditionEvaluationResult.disabled(String.format("Required profile '%s' is not active", profile));
            }
        }
        return ConditionEvaluationResult.enabled("All required profiles active: " + String.join(", ", profiles));
    }

}
