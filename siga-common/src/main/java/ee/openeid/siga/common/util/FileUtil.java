package ee.openeid.siga.common.util;

public class FileUtil {

    private static final char[] ILLEGAL_CHARACTERS = {'/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':'};


    public static boolean isFilenameValid(String file) {
        for (char illegalCharacter : ILLEGAL_CHARACTERS) {
            if (file.indexOf(illegalCharacter) > -1) {
                return false;
            }
        }
        return true;
    }
}
