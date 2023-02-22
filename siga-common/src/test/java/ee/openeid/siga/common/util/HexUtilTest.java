package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HexUtilTest {

    @Test
    public void validHexLowerCase() {
        assertTrue(HexUtil.isValidHex("09536f6d652072616e646f6d207465787420666f722074657374696e672e0a"));
    }

    @Test
    public void validHexUpperCase() {
        assertTrue(HexUtil.isValidHex("09536F6D652072616E646F6D207465787420666F722074657374696E672E0A"));
    }

    @Test
    public void oddNumberOfCharacters() {
        assertFalse(HexUtil.isValidHex("09536f6d652072616e646f6d207465787420666f722074657374696e672e0"));
    }

    @Test
    public void nonHexCharacters() {
        assertFalse(HexUtil.isValidHex("dGVzdHJhbmRvbQ=="));
    }

}