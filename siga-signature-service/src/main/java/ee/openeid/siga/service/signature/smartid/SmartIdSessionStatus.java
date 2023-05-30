package ee.openeid.siga.service.signature.smartid;

import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.ACCOUNT_NOT_FOUND;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.CERTIFICATE;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.DOCUMENT_NOT_USABLE;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.ERROR;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.EXPIRED_TRANSACTION;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.NOT_SUPPORTED;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.OUTSTANDING_TRANSACTION;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.SIGNATURE;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.USER_CANCEL;
import static ee.openeid.siga.service.signature.smartid.SmartIdSessionStatusMessages.WRONG_VC_SELECTED;

public enum SmartIdSessionStatus {
    OK(SIGNATURE, CERTIFICATE),
    USER_REFUSED(USER_CANCEL, USER_CANCEL),
    USER_REFUSED_CERT_CHOICE(USER_CANCEL, USER_CANCEL),
    USER_REFUSED_CONFIRMATIONMESSAGE(USER_CANCEL, USER_CANCEL),
    USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE(USER_CANCEL, USER_CANCEL),
    USER_REFUSED_DISPLAYTEXTANDPIN(USER_CANCEL, USER_CANCEL),
    USER_REFUSED_VC_CHOICE(USER_CANCEL, USER_CANCEL),
    WRONG_VC(WRONG_VC_SELECTED, WRONG_VC_SELECTED),
    TIMEOUT(EXPIRED_TRANSACTION, EXPIRED_TRANSACTION),
    DOCUMENT_UNUSABLE(DOCUMENT_NOT_USABLE, DOCUMENT_NOT_USABLE),
    REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP(NOT_SUPPORTED, NOT_SUPPORTED),
    RUNNING(OUTSTANDING_TRANSACTION, OUTSTANDING_TRANSACTION),

    USER_ACCOUNT_NOT_FOUND(ACCOUNT_NOT_FOUND, ACCOUNT_NOT_FOUND),
    INTERNAL_ERROR(ERROR, ERROR);

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
