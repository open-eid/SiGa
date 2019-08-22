package ee.openeid.siga.common.util;

import java.util.regex.Pattern;

public class HexUtil {

    private HexUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static Pattern pattern = Pattern.compile("^(?:[0-9A-Fa-f]{2})*$");

    public static boolean isValidHex(String hex) {
        return pattern.matcher(hex).matches();
    }

}
