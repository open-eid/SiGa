package ee.openeid.siga.service.signature.session;

import org.junit.Assert;
import org.junit.Test;

public class SessionIdGeneratorTest {

    @Test
    public void validSessionIdLength() {
        String sessionId = SessionIdGenerator.generateSessionId();
        Assert.assertEquals(36, sessionId.length());
    }
}
