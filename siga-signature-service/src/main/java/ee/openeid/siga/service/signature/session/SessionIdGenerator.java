package ee.openeid.siga.service.signature.session;

import java.util.UUID;

public class SessionIdGenerator {
    private SessionIdGenerator() {
    }

    public static String generateSessionId() {
        return UUID.randomUUID().toString();
    }
}
