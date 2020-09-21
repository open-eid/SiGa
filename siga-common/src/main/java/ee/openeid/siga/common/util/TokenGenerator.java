package ee.openeid.siga.common.util;

import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.util.Random;

public class TokenGenerator {

    private TokenGenerator() {
        throw new IllegalStateException("Utility class");
    }

    private static final Random random = new SecureRandom();

    public static String generateToken(final int length) {
        return RandomStringUtils.random(length, 0, 0, true, true, null, random);
    }
}
