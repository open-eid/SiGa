package ee.openeid.siga.auth;

import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import org.junit.Test;

import java.time.Instant;

import static java.time.Instant.now;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class TokenExpirationTest {

    private static final int TOKEN_EXPIRATION_INSECONDS = 10;
    private static final int ALLOWED_CLOCK_SKEW_IN_SECONDS = 5;
    private Instant SERVER_TIME = now();

    @Test
    public void shouldBeInvalid_WhenTokenIsSigned_InFuture() {
        Instant tokenTimestampInFuture = SERVER_TIME.plusSeconds(ALLOWED_CLOCK_SKEW_IN_SECONDS).plusMillis(1);
        assertFalse(HmacSignature.isTimestampValid(SERVER_TIME, tokenTimestampInFuture, TOKEN_EXPIRATION_INSECONDS, ALLOWED_CLOCK_SKEW_IN_SECONDS));
    }

    @Test
    public void shouldBeValid_WhenTokenIsSigned_ExactlyAtServertimeConsideringClockSkew() {
        Instant tokenTimestampExactlyAtServerTimeConsideringClockSkew = SERVER_TIME.plusSeconds(ALLOWED_CLOCK_SKEW_IN_SECONDS);
        assertTrue(HmacSignature.isTimestampValid(SERVER_TIME, tokenTimestampExactlyAtServerTimeConsideringClockSkew, TOKEN_EXPIRATION_INSECONDS, ALLOWED_CLOCK_SKEW_IN_SECONDS));
    }

    @Test
    public void shouldBeValid_WhenTokenIsSigned_ExactlyAtExpirationTimeConsideringClockSkew() {
        Instant tokenTimestampExactlyAtExpirationTimeConsideringClockSkew = SERVER_TIME.minusSeconds(TOKEN_EXPIRATION_INSECONDS).minusSeconds(ALLOWED_CLOCK_SKEW_IN_SECONDS);
        assertTrue(HmacSignature.isTimestampValid(SERVER_TIME, tokenTimestampExactlyAtExpirationTimeConsideringClockSkew, TOKEN_EXPIRATION_INSECONDS, ALLOWED_CLOCK_SKEW_IN_SECONDS));
    }

    @Test
    public void shouldBeInvalid_WhenTokenIsSigned_JustBeforeExpirationTime() {
        Instant tokenTimestampJustBeforeExpirationTime = SERVER_TIME.minusSeconds(TOKEN_EXPIRATION_INSECONDS).minusSeconds(ALLOWED_CLOCK_SKEW_IN_SECONDS).minusMillis(1);
        assertFalse(HmacSignature.isTimestampValid(SERVER_TIME, tokenTimestampJustBeforeExpirationTime, TOKEN_EXPIRATION_INSECONDS, ALLOWED_CLOCK_SKEW_IN_SECONDS));
    }

    @Test
    public void shouldBeValid_WhenTokenIsSigned_InPastButInExpirationRange() {
        Instant hmacTimestamp = SERVER_TIME.minusSeconds(TOKEN_EXPIRATION_INSECONDS / 2);
        assertTrue(HmacSignature.isTimestampValid(SERVER_TIME, hmacTimestamp, TOKEN_EXPIRATION_INSECONDS, ALLOWED_CLOCK_SKEW_IN_SECONDS));
    }
}
