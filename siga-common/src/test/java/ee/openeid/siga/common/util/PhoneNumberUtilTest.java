package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumberUtilTest {

    @Test
    void validPhoneNumber() {
        assertTrue(PhoneNumberUtil.isPhoneNumberValid("+37253483726"));
    }

    @Test
    void phoneNumberWithoutPlusSign() {
        assertFalse(PhoneNumberUtil.isPhoneNumberValid("37253483726"));
    }

    @Test
    void phoneNumberTooShort() {
        assertFalse(PhoneNumberUtil.isPhoneNumberValid("+372873"));
    }

    @Test
    void phoneNumberTooLong() {
        assertFalse(PhoneNumberUtil.isPhoneNumberValid("+372538737293729373"));
    }

    @Test
    void countryPrefixIsNotInList(){
        assertNull(PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("+371"));
    }

    @Test
    void invalidCountryPrefix(){
        assertNull(PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("random"));
    }

    @Test
    void validCountryPrefix(){
        assertEquals(PhoneNumberUtil.CountryCallingCode.EE, PhoneNumberUtil.CountryCallingCode.getCountryByPrefix("+372"));
    }
}
