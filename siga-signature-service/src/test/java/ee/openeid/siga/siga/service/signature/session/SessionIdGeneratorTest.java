package ee.openeid.siga.siga.service.signature.session;

import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import org.junit.Assert;
import org.junit.Test;

public class SessionIdGeneratorTest {

    @Test
    public void validSessionIdLength() {
        String sessionId = SessionIdGenerator.generateSessionId();
        Assert.assertEquals(36, sessionId.length());
    }
}
