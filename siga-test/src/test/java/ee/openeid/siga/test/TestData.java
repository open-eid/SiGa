package ee.openeid.siga.test;

public class TestData {

    // Endpoints
    public static final String HASHCODE_CONTAINERS = "/hashcodecontainers";
    public static final String VALIDATIONREPORT = "/validationreport";
    public static final String UPLOAD_HASHCODE_CONTAINERS = "/upload/hashcodecontainers";

    // Headers for HMAC authentication
    public static final String X_AUTHORIZATION_TIMESTAMP = "X-Authorization-Timestamp";
    public static final String X_AUTHORIZATION_SERVICE_UUID = "X-Authorization-ServiceUUID";
    public static final String X_AUTHORIZATION_SIGNATURE = "X-Authorization-Signature";
    public static final String X_AUTHORIZATION_HMAC_ALGO = "X-Authorization-Hmac-Algorithm";

    // User information
    public static final String SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    public static final String SERVICE_SECRET = "746573745365637265744b6579303031";

    // Hashcode datafile mock strings
    public static final String DEFAULT_SHA256_DATAFILE = "RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo";
    public static final String DEFAULT_SHA512_DATAFILE = "hQVz9wirVZNvP/q3HoaW8nu0FfvrGkZinhADKE4Y4j/dUuGfgONfR4VYdu0p/dj/yGH0qlE0FGsmUB2N3oLuhA==";
    public static final String DEFAULT_FILENAME = "test.txt";
    public static final String DEFAULT_FILESIZE = "745";

    // Response strings
    public static final String CONTAINER_ID = "containerId";

    // Error response strings
    public static final String ERROR_CODE = "errorCode";

    // Error codes
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";

}
