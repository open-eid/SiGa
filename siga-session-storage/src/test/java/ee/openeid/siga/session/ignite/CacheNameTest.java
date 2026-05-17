package ee.openeid.siga.session.ignite;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the three Ignite cache-name constants. The values are referenced by Spring XML bean names in
 * {@code ignite-configuration.xml}, by {@link IgniteSessionStorage}, {@link IgniteSessionExpiryNotifier}
 * and {@link IgniteSessionStatusScanner}; a rename or addition would break those wirings silently
 * (cache miss / cache auto-creation with default settings).
 */
class CacheNameTest {

    @Test
    void shouldExposeThreeCacheNames() {
        assertEquals(3, CacheName.values().length,
                "CacheName is consumed by ignite-configuration.xml and three storage components — "
                        + "adding or removing a constant requires updating all of them");
    }

    @Test
    void shouldUseTheExactNamesIgniteConfigurationXmlExpects() {
        Set<String> names = Arrays.stream(CacheName.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("CONTAINER_SESSION", "SIGNATURE_SESSION", "CERTIFICATE_SESSION"), names);
    }
}
