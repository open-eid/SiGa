package ee.openeid.siga.test;

public class TestData {

    // Endpoints
    public static final String HASHCODE_CONTAINERS = "/hashcodecontainers";
    public static final String VALIDATIONREPORT = "/validationreport";
    public static final String UPLOAD = "/upload";
    public static final String REMOTE_SIGNING = "/remotesigning";
    public static final String MID_SIGNING = "/mobileidsigning";
    public static final String STATUS = "/status";
    public static final String SIGNATURES = "/signatures";


    // Headers for HMAC authentication
    public static final String X_AUTHORIZATION_TIMESTAMP = "X-Authorization-Timestamp";
    public static final String X_AUTHORIZATION_SERVICE_UUID = "X-Authorization-ServiceUUID";
    public static final String X_AUTHORIZATION_SIGNATURE = "X-Authorization-Signature";
    public static final String X_AUTHORIZATION_HMAC_ALGO = "X-Authorization-Hmac-Algorithm";

    // User information
    public static final String SERVICE_UUID_1 = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    public static final String SERVICE_SECRET_1 = "746573745365637265744b6579303031";

    public static final String SERVICE_UUID_2 = "824dcfe9-5c26-4d76-829a-e6630f434746";
    public static final String SERVICE_SECRET_2 = "746573745365637265744b6579303032";

    // Hashcode datafile mock strings
    public static final String DEFAULT_SHA256_DATAFILE = "RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo";
    public static final String DEFAULT_SHA512_DATAFILE = "hQVz9wirVZNvP/q3HoaW8nu0FfvrGkZinhADKE4Y4j/dUuGfgONfR4VYdu0p/dj/yGH0qlE0FGsmUB2N3oLuhA==";
    public static final String DEFAULT_FILENAME = "test.txt";
    public static final String DEFAULT_FILESIZE = "745";

    // Signing mock strings
    public static final String SIGNER_CERT_PEM = "MIID6jCCA02gAwIBAgIQR+qcVFxYF1pcSy/QGEnMVjAKBggqhkjOPQQDBDBgMQswCQYDVQQGEwJFRTEbMBkGA1UECgwSU0sgSUQgU29sdXRpb25zIEFTMRcwFQYDVQRhDA5OVFJFRS0xMDc0NzAxMzEbMBkGA1UEAwwSVEVTVCBvZiBFU1RFSUQyMDE4MB4XDTE5MDEyNTE1NDgzMVoXDTI0MDEyNTIxNTk1OVowfzELMAkGA1UEBhMCRUUxKjAoBgNVBAMMIUrDlUVPUkcsSkFBSy1LUklTVEpBTiwzODAwMTA4NTcxODEQMA4GA1UEBAwHSsOVRU9SRzEWMBQGA1UEKgwNSkFBSy1LUklTVEpBTjEaMBgGA1UEBRMRUE5PRUUtMzgwMDEwODU3MTgwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATbyCq95SWCQTr+b5MXxRLTHYHJHCgaLornlrF9j+q6aFCDFLgoNv70yw/sHYp2FQ0yRywG2vFwDCLA5vACPLSVPGyOvYx7fiX84uSpPo6fcNlwQ25coNfpUIIuh+T6MwujggGrMIIBpzAJBgNVHRMEAjAAMA4GA1UdDwEB/wQEAwIGQDBIBgNVHSAEQTA/MDIGCysGAQQBg5EhAQIBMCMwIQYIKwYBBQUHAgEWFWh0dHBzOi8vd3d3LnNrLmVlL0NQUzAJBgcEAIvsQAECMB0GA1UdDgQWBBTIgEaf0wSPZSWihjLuyTNmzm4DWzCBigYIKwYBBQUHAQMEfjB8MAgGBgQAjkYBATAIBgYEAI5GAQQwEwYGBACORgEGMAkGBwQAjkYBBgEwUQYGBACORgEFMEcwRRY/aHR0cHM6Ly9zay5lZS9lbi9yZXBvc2l0b3J5L2NvbmRpdGlvbnMtZm9yLXVzZS1vZi1jZXJ0aWZpY2F0ZXMvEwJFTjAfBgNVHSMEGDAWgBTAhJkpxE6fOwI09pnhClYACCk+ezBzBggrBgEFBQcBAQRnMGUwLAYIKwYBBQUHMAGGIGh0dHA6Ly9haWEuZGVtby5zay5lZS9lc3RlaWQyMDE4MDUGCCsGAQUFBzAChilodHRwOi8vYy5zay5lZS9UZXN0X29mX0VTVEVJRDIwMTguZGVyLmNydDAKBggqhkjOPQQDBAOBigAwgYYCQSPBHYO2O/aLmr+vqMlESJrIY3gdtWni8hd4phIl5fR3uiQaQvtNeGBIzrGvdqgRJmYg+HvskQb/Laq7Xjp+cgqkAkEX9+x/S3H/S/+n/nogfgRSP5JCwYAw02zTRL3MKLpZ1AOf8i1iGvpHI9S6iyXcDhh6hM8slDg7EK3KyNwfkMLh5A==";

    // Response strings
    public static final String CONTAINER_ID = "containerId";
    public static final String RESULT = "result";
    public static final String DATA_TO_SIGN = "dataToSign";
    public static final String DIGEST_ALGO = "digestAlgorithm";

    // Validation response strings
    public static final String REPORT_VALID_SIGNATURES_COUNT = "validationConclusion.validSignaturesCount";
    public static final String REPORT_SIGNATURES_COUNT = "validationConclusion.signaturesCount";
    public static final String REPORT_SIGNATURES = "validationConclusion.signatures";

    // Error response strings
    public static final String ERROR_CODE = "errorCode";
    public static final String ERROR_MESSAGE = "errorMessage";

    // Error codes
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND_EXCEPTION";
    public static final String INVALID_REQUEST = "INVALID_REQUEST_EXCEPTION";

}
