package ee.openeid.siga.common.util;

import org.junit.Assert;
import org.junit.Test;

public class PhoneNumberUtilTest {

    @Test
    public void validPhoneNumber() {
        Assert.assertTrue(PhoneNumberUtil.isPhoneNumberValid("+37253483726"));
    }

    @Test
    public void phoneNumberWithoutPlusSign() {
        Assert.assertFalse(PhoneNumberUtil.isPhoneNumberValid("37253483726"));
    }

    @Test
    public void phoneNumberTooShort() {
        Assert.assertFalse(PhoneNumberUtil.isPhoneNumberValid("+372873"));
    }

    @Test
    public void phoneNumberTooLong() {
        Assert.assertFalse(PhoneNumberUtil.isPhoneNumberValid("+372538737293729373"));
    }
}
