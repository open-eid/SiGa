package ee.openeid.siga.service.signature.smartid;

public enum SmartIdErrorStatus {
    NOT_FOUND("NOT_FOUND"),
    SESSION_NOT_FOUND("SESSION_NOT_FOUND");

    private final String sigaMessage;

    SmartIdErrorStatus(String sigaMessage) {
        this.sigaMessage = sigaMessage;
    }

    public String getSigaMessage() {
        return this.sigaMessage;
    }

}
