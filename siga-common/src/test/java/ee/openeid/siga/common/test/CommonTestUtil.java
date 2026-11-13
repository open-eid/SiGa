package ee.openeid.siga.common.test;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.junit.jupiter.params.provider.Arguments;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommonTestUtil {

    public static Arguments concatArguments(Arguments... arguments) {
        return asArguments(
                Stream.of(arguments)
                        .map(Arguments::get)
                        .flatMap(Stream::of)
        );
    }

    public static Arguments concatArguments(Arguments arguments, Object argument) {
        return asArguments(
                Stream.of(arguments.get()),
                Stream.of(argument)
        );
    }

    public static Arguments concatArguments(Arguments arguments1, Object... arguments2) {
        return asArguments(
                Stream.of(arguments1.get()),
                Stream.of(arguments2)
        );
    }

    public static Arguments concatArguments(Object argument, Arguments arguments) {
        return asArguments(
                Stream.of(argument),
                Stream.of(arguments.get())
        );
    }

    public static Arguments concatArguments(Object[] arguments1, Arguments arguments2) {
        return asArguments(
                Stream.of(arguments1),
                Stream.of(arguments2.get())
        );
    }

    @SafeVarargs
    private static Arguments asArguments(Stream<Object>... argumentStreams) {
        return asArguments(Stream.of(argumentStreams).flatMap(UnaryOperator.identity()));
    }

    private static Arguments asArguments(Stream<Object> argumentStream) {
        return Arguments.of(argumentStream.toArray());
    }

}
