package ee.openeid.siga.common.test;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Generators {

    /**
     * Creates a supplier that returns the specified values in order, repeating the last value infinitively.
     * At least one value must be specified!
     *
     * @param values an array of values to return in order
     * @return the next value from the specified list of values, or the last value if the end of the list has been reached
     * @param <T> value type
     *
     * @see #infinite(List)
     */
    @SafeVarargs
    public static <T> Supplier<T> infinite(T... values) {
        return infinite(List.of(values));
    }

    /**
     * Creates a supplier that returns the specified values in order, repeating the last value infinitively.
     * At least one value must be specified!
     *
     * @param values a list of values to return in order
     * @return the next value from the specified list of values, or the last value if the end of the list has been reached
     * @param <T> value type
     *
     * @see #infinite(Object[])
     */
    public static <T> Supplier<T> infinite(List<T> values) {
        if (CollectionUtils.isEmpty(values)) {
            throw new IllegalArgumentException("Infinite generator must be provided with at least one value");
        }

        final T last = values.get(values.size() - 1);

        return Stream.concat(
                values.stream(),
                Stream.generate(() -> last)
        )
                .iterator()::next;
    }

}
