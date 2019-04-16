package ee.openeid.siga.common.util;

import java.util.regex.Pattern;

public class Base64Util {

    private static Pattern pattern = Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");

    public static boolean isValidBase64(String base64) {
        return pattern.matcher(base64).matches();
    }
}
