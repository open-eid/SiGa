package ee.openeid.siga.common.util;

import java.util.UUID;

public class UUIDGenerator {
    private UUIDGenerator() {
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
