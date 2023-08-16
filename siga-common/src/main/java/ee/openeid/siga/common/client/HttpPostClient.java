package ee.openeid.siga.common.client;

@FunctionalInterface
public interface HttpPostClient {

    <T> T post(String path, Object request, Class<T> responseType);
}
