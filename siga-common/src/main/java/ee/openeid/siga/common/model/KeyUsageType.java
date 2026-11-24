package ee.openeid.siga.common.model;

/**
 * KeyUsage type as defined in @see
 * <a href="https://datatracker.ietf.org/doc/html/rfc5280">RFC 5280</a>.
 */
public class KeyUsageType
{
    public static final int DIGITAL_SIGNATURE  = 0;
    public static final int NON_REPUDIATION    = 1;
    public static final int CONTENT_COMMITMENT = NON_REPUDIATION;
    public static final int KEY_ENCIPHERMENT   = 2;
    public static final int DATA_ENCIPHERMENT  = 3;
    public static final int KEY_AGREEMENT      = 4;
    public static final int KEY_CERT_SIGN      = 5;
    public static final int CRL_SIGN           = 6;
    public static final int ENCIPHER_ONLY      = 7;
    public static final int DECIPHER_ONLY      = 8;
}
