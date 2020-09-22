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

    @Test
    public void countryPrefixIsNotInList(){
        Assert.assertNull(PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("+371"));
    }

    @Test
    public void invalidCountryPrefix(){
        Assert.assertNull(PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("random"));
    }

    @Test
    public void validCountryPrefix(){
        Assert.assertEquals(PhoneNumberUtil.CountryCallingCode.EE, PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("+372"));
    }
}
