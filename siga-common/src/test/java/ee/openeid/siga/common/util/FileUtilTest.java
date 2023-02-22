package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilTest {

    @Test
    public void validFileName() {
        assertTrue(FileUtil.isFilenameValid("fi le12%&.txt"));
    }

    @Test
    public void fileNameWithoutExtension() {
        assertTrue(FileUtil.isFilenameValid("file"));
    }

    @Test
    public void asteriskIncludedInFileName() {
        assertFalse(FileUtil.isFilenameValid("*.txt"));
    }

    @Test
    public void FileNameWithDirectory() {
        assertFalse(FileUtil.isFilenameValid("Data/file.txt"));
    }
}
