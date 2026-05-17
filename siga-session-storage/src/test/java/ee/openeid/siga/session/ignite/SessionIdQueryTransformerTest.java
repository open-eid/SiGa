package ee.openeid.siga.session.ignite;

import org.apache.ignite.binary.BinaryObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.cache.Cache;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the contract that {@link SessionStatusScanner}'s {@code ScanQuery} projection extracts the
 * cache key unchanged. The class is loaded into Ignite server nodes via peer class loading; it
 * must be {@link Serializable} and the {@code apply()} contract must be a pure key passthrough so
 * the scanner emits real session ids, not transformed/mangled strings.
 */
class SessionIdQueryTransformerTest {

    @Test
    void shouldReturnEntryKeyUnchanged() {
        Cache.Entry<String, Map<String, BinaryObject>> entry = Mockito.mock(Cache.Entry.class);
        Mockito.when(entry.getKey()).thenReturn("v1_svc_xyz");

        SessionIdQueryTransformer transformer = new SessionIdQueryTransformer();

        assertEquals("v1_svc_xyz", transformer.apply(entry),
                "The scanner relies on the transformer being a pure key passthrough — any "
                        + "modification would silently emit wrong session ids to reprocessor consumers");
    }

    @Test
    void shouldBeSerializable_ForPeerClassLoading() throws IOException {
        // IgniteClosure instances ship over the wire to server nodes. If the class ever stops
        // being Serializable (e.g. picks up a non-serializable field), this round-trip fails fast.
        SessionIdQueryTransformer transformer = new SessionIdQueryTransformer();
        assertTrue(transformer instanceof Serializable,
                "Must be Serializable for Ignite peer class loading");

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(transformer);
            assertNotNull(bytes.toByteArray());
        }
    }
}
