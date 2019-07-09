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
}
