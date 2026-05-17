package ee.openeid.siga.common.model;

public enum SigningType {
    REMOTE,
    MOBILE_ID,
    SMART_ID;

    public boolean isPollable() {
        return this == SMART_ID || this == MOBILE_ID;
    }

    public static boolean isPollable(SigningType signingType) {
        return signingType != null && signingType.isPollable();
    }
}
