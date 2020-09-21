package ee.openeid.siga.service.signature.smartid;

public enum SmartIdSessionStatus {
    OK("SIGNATURE", "CERTIFICATE"),
    USER_REFUSED("USER_CANCEL", "USER_CANCEL"),
    TIMEOUT("EXPIRED_TRANSACTION", "EXPIRED_TRANSACTION"),
    DOCUMENT_UNUSABLE("DOCUMENT_UNUSABLE", "DOCUMENT_UNUSABLE"),
    RUNNING("OUTSTANDING_TRANSACTION", "OUTSTANDING_TRANSACTION");

    private final String sigaSignMessage;
    private final String sigaCertMessage;

    SmartIdSessionStatus(String sigaSignMessage, String sigaCertMessage) {
        this.sigaSignMessage = sigaSignMessage;
        this.sigaCertMessage = sigaCertMessage;
    }

    public String getSigaMessage(SessionType type) {
        if (SessionType.SIGN == type) {
            return sigaSignMessage;
        }
        return sigaCertMessage;
    }

    public enum SessionType {
        CERT,
        SIGN
    }
}
