package ee.openeid.siga.service.signature.smartid;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SmartIdSessionStatusMessages {
    public static final String SIGNATURE = "SIGNATURE";
    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String USER_CANCEL = "USER_CANCEL";
    public static final String WRONG_VC_SELECTED = "USER_SELECTED_WRONG_VC";
    public static final String EXPIRED_TRANSACTION = "EXPIRED_TRANSACTION";
    public static final String DOCUMENT_NOT_USABLE = "DOCUMENT_UNUSABLE";
    public static final String NOT_SUPPORTED = "NOT_SUPPORTED_BY_APP";
    public static final String OUTSTANDING_TRANSACTION = "OUTSTANDING_TRANSACTION";
    public static final String ACCOUNT_NOT_FOUND = "USER_ACCOUNT_NOT_FOUND";
    public static final String ERROR = "INTERNAL_ERROR";
}
