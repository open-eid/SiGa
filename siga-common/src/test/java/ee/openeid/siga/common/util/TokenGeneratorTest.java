package ee.openeid.siga.common.util;

import org.junit.Assert;
import org.junit.Test;

public class TokenGeneratorTest {

    @Test
    public void generateToken_0Length(){
        String token = TokenGenerator.generateToken(0);
        Assert.assertEquals("", token);
    }

    @Test
    public void generateToken_1Length(){
        String token = TokenGenerator.generateToken(1);
        Assert.assertEquals(1, token.length());
    }

    @Test
    public void generateToken_100Length(){
        String token = TokenGenerator.generateToken(100);
        Assert.assertEquals(100, token.length());
    }

    @Test
    public void differentTokenEveryTime(){
        String token1 = TokenGenerator.generateToken(100);
        String token2 = TokenGenerator.generateToken(100);
        Assert.assertNotEquals(token1, token2);
    }
}
