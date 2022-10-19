package ee.openeid.siga.service.signature.smartid;

public enum SmartIdSessionStatus {
    OK("SIGNATURE", "CERTIFICATE"),
    USER_REFUSED("USER_CANCEL", "USER_CANCEL"),
    USER_REFUSED_CERT_CHOICE("USER_CANCEL", "USER_CANCEL"),
    USER_REFUSED_CONFIRMATIONMESSAGE("USER_CANCEL", "USER_CANCEL"),
    USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE("USER_CANCEL", "USER_CANCEL"),
    USER_REFUSED_DISPLAYTEXTANDPIN("USER_CANCEL", "USER_CANCEL"),
    USER_REFUSED_VC_CHOICE("USER_CANCEL", "USER_CANCEL"),
    WRONG_VC("USER_SELECTED_WRONG_VC", "USER_SELECTED_WRONG_VC"),
    TIMEOUT("EXPIRED_TRANSACTION", "EXPIRED_TRANSACTION"),
    DOCUMENT_UNUSABLE("DOCUMENT_UNUSABLE", "DOCUMENT_UNUSABLE"),
    REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP("NOT_SUPPORTED_BY_APP", "NOT_SUPPORTED_BY_APP"),
    RUNNING("OUTSTANDING_TRANSACTION", "OUTSTANDING_TRANSACTION"),

    USER_ACCOUNT_NOT_FOUND("USER_ACCOUNT_NOT_FOUND", "USER_ACCOUNT_NOT_FOUND"),
    INTERNAL_ERROR("INTERNAL_ERROR", "INTERNAL_ERROR");

    private final String sigaSignMessage;
    private final String sigaCertMessage;

    SmartIdSessionStatus(String sigaSignMessage, String sigaCertMessage) {
        this.sigaSignMessage = sigaSignMessage;
        this.sigaCertMessage = sigaCertMessage;
    }

    public String getSigaSigningMessage() {
        return sigaSignMessage;
    }

    public String getSigaCertificateMessage() {
        return sigaCertMessage;
    }
}
