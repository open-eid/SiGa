package ee.openeid.siga.common.event;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import lombok.SneakyThrows;
import org.junit.jupiter.api.function.Executable;

import java.util.Map;
import java.util.concurrent.Callable;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SigaEventLoggingAspectTestUtil {

    @SneakyThrows
    public static void executeInParameterContext(Executable executable) {
        SigaEventLoggingAspect.setUpParameterContext();
        try {
            executable.execute();
        } finally {
            SigaEventLoggingAspect.clearParameterContext();
        }
    }

    @SneakyThrows
    public static <R> R callInParameterContext(Callable<R> callable) {
        SigaEventLoggingAspect.setUpParameterContext();
        try {
            return callable.call();
        } finally {
            SigaEventLoggingAspect.clearParameterContext();
        }
    }

    @SneakyThrows
    public static Map<String, String> executeAndReturnContext(Executable executable) {
        SigaEventLoggingAspect.setUpParameterContext();
        try {
            executable.execute();
            return SigaEventLoggingAspect.getContextMap();
        } finally {
            SigaEventLoggingAspect.clearParameterContext();
        }
    }
}
