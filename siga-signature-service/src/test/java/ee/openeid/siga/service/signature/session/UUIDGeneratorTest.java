package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.util.UUIDGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UUIDGeneratorTest {

    @Test
    public void validSessionIdLength() {
        String sessionId = UUIDGenerator.generateUUID();
        assertEquals(36, sessionId.length());
    }
}
