package ee.openeid.siga.session;

public interface SessionService {

    String getContainer(String sessionId);

    void update(String sessionId, String container);


}
