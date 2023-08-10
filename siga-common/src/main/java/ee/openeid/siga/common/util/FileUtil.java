package ee.openeid.siga.common.util;

import java.util.regex.Pattern;

public class FileUtil {

    private FileUtil() {
        throw new IllegalStateException("Utility class");
    }

    private static final Pattern CONTAINS_CONTROL_CHARACTERS_PATTERN = Pattern.compile(".*\\p{Cntrl}.*");
    private static final char[] OTHER_ILLEGAL_CHARACTERS = {'/', '`', '?', '*', '\\', '<', '>', '|', '\"', ':'};

    public static boolean isFilenameValid(String fileName) {
        for (char illegalCharacter : OTHER_ILLEGAL_CHARACTERS) {
            if (fileName.indexOf(illegalCharacter) > -1) {
                return false;
            }
        }
        return !CONTAINS_CONTROL_CHARACTERS_PATTERN.matcher(fileName).matches();
    }
}
