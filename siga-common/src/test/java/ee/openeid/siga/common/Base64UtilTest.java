package ee.openeid.siga.common;

import org.junit.Assert;
import org.junit.Test;

public class Base64UtilTest {

    @Test
    public void validBase64() {
        Assert.assertTrue(Base64Util.isValidBase64("dGVzdHJhbmRvbQ=="));
    }

    @Test
    public void noPadding() {
        Assert.assertFalse(Base64Util.isValidBase64("dGVzdHJhbmRvbQ"));
    }

    @Test
    public void extraPadding() {
        Assert.assertFalse(Base64Util.isValidBase64("dGVzdHJhbmRvbQ==="));
    }
}
