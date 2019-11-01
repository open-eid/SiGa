package ee.openeid.siga.common.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Pattern;

public class UUIDGeneratorTest {

    private static Pattern pattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Test
    public void generateValidUUID(){
        String uuid = UUIDGenerator.generateUUID();
        Assert.assertTrue(pattern.matcher(uuid).matches());
    }
}
