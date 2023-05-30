package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilTest {

    @Test
    void validFileName() {
        assertTrue(FileUtil.isFilenameValid("fi le12%&.txt"));
    }

    @Test
    void fileNameWithoutExtension() {
        assertTrue(FileUtil.isFilenameValid("file"));
    }

    @Test
    void asteriskIncludedInFileName() {
        assertFalse(FileUtil.isFilenameValid("*.txt"));
    }

    @Test
    void FileNameWithDirectory() {
        assertFalse(FileUtil.isFilenameValid("Data/file.txt"));
    }
}
