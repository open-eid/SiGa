package ee.openeid.siga.service.signature.smartid;

public enum SmartIdSessionStatus {
    OK("SIGNATURE"),
    USER_REFUSED("USER_CANCEL"),
    TIMEOUT("EXPIRED_TRANSACTION"),
    DOCUMENT_UNUSABLE("DOCUMENT_UNUSABLE"),
    RUNNING("OUTSTANDING_TRANSACTION");

    private final String sigaMessage;

    SmartIdSessionStatus(String sigaMessage) {
        this.sigaMessage = sigaMessage;
    }

    public String getSigaMessage() {
        return sigaMessage;
    }
}
