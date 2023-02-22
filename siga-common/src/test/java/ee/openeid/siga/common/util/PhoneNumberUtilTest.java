package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PhoneNumberUtilTest {

    @Test
    public void validPhoneNumber() {
        assertTrue(PhoneNumberUtil.isPhoneNumberValid("+37253483726"));
    }

    @Test
    public void phoneNumberWithoutPlusSign() {
        assertFalse(PhoneNumberUtil.isPhoneNumberValid("37253483726"));
    }

    @Test
    public void phoneNumberTooShort() {
        assertFalse(PhoneNumberUtil.isPhoneNumberValid("+372873"));
    }

    @Test
    public void phoneNumberTooLong() {
        assertFalse(PhoneNumberUtil.isPhoneNumberValid("+372538737293729373"));
    }

    @Test
    public void countryPrefixIsNotInList(){
        assertNull(PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("+371"));
    }

    @Test
    public void invalidCountryPrefix(){
        assertNull(PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("random"));
    }

    @Test
    public void validCountryPrefix(){
        assertEquals(PhoneNumberUtil.CountryCallingCode.EE, PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("+372"));
    }
}
