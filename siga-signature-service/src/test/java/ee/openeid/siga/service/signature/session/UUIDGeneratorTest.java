package ee.openeid.siga.service.signature.session;

import ee.openeid.siga.common.util.UUIDGenerator;
import org.junit.Assert;
import org.junit.Test;

public class UUIDGeneratorTest {

    @Test
    public void validSessionIdLength() {
        String sessionId = UUIDGenerator.generateUUID();
        Assert.assertEquals(36, sessionId.length());
    }
}
