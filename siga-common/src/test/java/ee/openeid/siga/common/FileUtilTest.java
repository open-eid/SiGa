package ee.openeid.siga.common;

import org.junit.Assert;
import org.junit.Test;

public class FileUtilTest {

    @Test
    public void validFileName() {
        Assert.assertTrue(FileUtil.isFilenameValid("fi le12%&.txt"));
    }

    @Test
    public void fileNameWithoutExtension() {
        Assert.assertTrue(FileUtil.isFilenameValid("file"));
    }

    @Test
    public void asteriskIncludedInFileName() {
        Assert.assertFalse(FileUtil.isFilenameValid("*.txt"));
    }

    @Test
    public void FileNameWithDirectory() {
        Assert.assertFalse(FileUtil.isFilenameValid("Data/file.txt"));
    }
}
