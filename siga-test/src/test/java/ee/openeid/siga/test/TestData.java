package ee.openeid.siga.test;

public class TestData {

    // Endpoints
    public static final String HASHCODE_CONTAINERS = "/hashcodecontainers";
    public static final String VALIDATIONREPORT = "/validationreport";
    public static final String UPLOAD = "/upload";
    public static final String REMOTESIGNING = "/remotesigning";


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

    // Signing mock strings
    public static final String SIGNER_CERT_PEM = "-----BEGIN CERTIFICATE-----\n" +
            "MIID6jCCA02gAwIBAgIQR+qcVFxYF1pcSy/QGEnMVjAKBggqhkjOPQQDBDBgMQsw\n" +
            "CQYDVQQGEwJFRTEbMBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMRcwFQYDVQRh\n" +
            "DA5OVFJFRS0xMDc0NzAxMzEbMBkGA1UEAwwSVEVTVCBvZiBFU1RFSUQyMDE4MB4X\n" +
            "DTE5MDEyNTE1NDgzMVoXDTI0MDEyNTIxNTk1OVowfzELMAkGA1UEBhMCRUUxKjAo\n" +
            "BgNVBAMMIUrDlUVPUkcsSkFBSy1LUklTVEpBTiwzODAwMTA4NTcxODEQMA4GA1UE\n" +
            "BAwHSsOVRU9SRzEWMBQGA1UEKgwNSkFBSy1LUklTVEpBTjEaMBgGA1UEBRMRUE5P\n" +
            "RUUtMzgwMDEwODU3MTgwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATbyCq95SWCQTr+\n" +
            "b5MXxRLTHYHJHCgaLornlrF9j+q6aFCDFLgoNv70yw/sHYp2FQ0yRywG2vFwDCLA\n" +
            "5vACPLSVPGyOvYx7fiX84uSpPo6fcNlwQ25coNfpUIIuh+T6MwujggGrMIIBpzAJ\n" +
            "BgNVHRMEAjAAMA4GA1UdDwEB/wQEAwIGQDBIBgNVHSAEQTA/MDIGCysGAQQBg5Eh\n" +
            "AQIBMCMwIQYIKwYBBQUHAgEWFWh0dHBzOi8vd3d3LnNrLmVlL0NQUzAJBgcEAIvs\n" +
            "QAECMB0GA1UdDgQWBBTIgEaf0wSPZSWihjLuyTNmzm4DWzCBigYIKwYBBQUHAQME\n" +
            "fjB8MAgGBgQAjkYBATAIBgYEAI5GAQQwEwYGBACORgEGMAkGBwQAjkYBBgEwUQYG\n" +
            "BACORgEFMEcwRRY/aHR0cHM6Ly9zay5lZS9lbi9yZXBvc2l0b3J5L2NvbmRpdGlv\n" +
            "bnMtZm9yLXVzZS1vZi1jZXJ0aWZpY2F0ZXMvEwJFTjAfBgNVHSMEGDAWgBTAhJkp\n" +
            "xE6fOwI09pnhClYACCk+ezBzBggrBgEFBQcBAQRnMGUwLAYIKwYBBQUHMAGGIGh0\n" +
            "dHA6Ly9haWEuZGVtby5zay5lZS9lc3RlaWQyMDE4MDUGCCsGAQUFBzAChilodHRw\n" +
            "Oi8vYy5zay5lZS9UZXN0X29mX0VTVEVJRDIwMTguZGVyLmNydDAKBggqhkjOPQQD\n" +
            "BAOBigAwgYYCQSPBHYO2O/aLmr+vqMlESJrIY3gdtWni8hd4phIl5fR3uiQaQvtN\n" +
            "eGBIzrGvdqgRJmYg+HvskQb/Laq7Xjp+cgqkAkEX9+x/S3H/S/+n/nogfgRSP5JC\n" +
            "wYAw02zTRL3MKLpZ1AOf8i1iGvpHI9S6iyXcDhh6hM8slDg7EK3KyNwfkMLh5A==\n" +
            "-----END CERTIFICATE-----";

    // Response strings
    public static final String CONTAINER_ID = "containerId";

    // Error response strings
    public static final String ERROR_CODE = "errorCode";

    // Error codes
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";

}
