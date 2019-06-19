package ee.openeid.siga.common.session;

public interface Session {
    String getClientName();

    String getServiceName();

    String getServiceUuid();

    String getSessionId();

    void addDataToSign(String signatureId, DataToSignHolder dataToSign);

    DataToSignHolder getDataToSignHolder(String signatureId);
}
