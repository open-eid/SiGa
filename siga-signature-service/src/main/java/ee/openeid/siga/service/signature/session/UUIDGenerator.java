package ee.openeid.siga.service.signature.session;

import java.util.UUID;

public class UUIDGenerator {
    private UUIDGenerator() {
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
