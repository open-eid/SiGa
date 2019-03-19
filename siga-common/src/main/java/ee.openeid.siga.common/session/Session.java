package ee.openeid.siga.common.session;

public interface Session {
    String getClientName();

    String getServiceName();

    String getServiceUuid();

    String getSessionId();
}
