package ee.openeid.siga.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TokenGeneratorTest {

    @Test
    public void generateToken_0Length(){
        String token = TokenGenerator.generateToken(0);
        assertEquals("", token);
    }

    @Test
    public void generateToken_1Length(){
        String token = TokenGenerator.generateToken(1);
        assertEquals(1, token.length());
    }

    @Test
    public void generateToken_100Length(){
        String token = TokenGenerator.generateToken(100);
        assertEquals(100, token.length());
    }

    @Test
    public void differentTokenEveryTime(){
        String token1 = TokenGenerator.generateToken(100);
        String token2 = TokenGenerator.generateToken(100);
        assertNotEquals(token1, token2);
    }
}
