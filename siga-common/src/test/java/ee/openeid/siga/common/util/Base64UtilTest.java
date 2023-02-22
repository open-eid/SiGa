package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Base64UtilTest {

    @Test
    public void validBase64() {
        assertTrue(Base64Util.isValidBase64("dGVzdHJhbmRvbQ=="));
    }

    @Test
    public void noPadding() {
        assertFalse(Base64Util.isValidBase64("dGVzdHJhbmRvbQ"));
    }

    @Test
    public void extraPadding() {
        assertFalse(Base64Util.isValidBase64("dGVzdHJhbmRvbQ==="));
    }
}
