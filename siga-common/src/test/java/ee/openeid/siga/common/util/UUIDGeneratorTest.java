package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UUIDGeneratorTest {

    private static Pattern pattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Test
    public void generateValidUUID(){
        String uuid = UUIDGenerator.generateUUID();
        assertTrue(pattern.matcher(uuid).matches());
    }
}
