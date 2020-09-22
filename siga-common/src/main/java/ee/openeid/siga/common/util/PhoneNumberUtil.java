package ee.openeid.siga.common.util;

import java.util.regex.Pattern;

public class PhoneNumberUtil {

    private PhoneNumberUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static Pattern pattern = Pattern.compile("^\\+[0-9]{7,15}$");

    public static boolean isPhoneNumberValid(String phoneNumber) {
        return pattern.matcher(phoneNumber).matches();
    }

    public enum CountryCallingCode {
        EE("+372"),
        LT("+370");

        private final String prefix;


        CountryCallingCode(String prefix) {
            this.prefix = prefix;
        }

        public static CountryCallingCode getCountryByPrefix(String prefix) {
            CountryCallingCode[] values = CountryCallingCode.values();
            for (CountryCallingCode value : values) {
                if (value.prefix.equals(prefix)) {
                    return value;
                }
            }
            return null;
        }

    }
}
