package ee.openeid.siga.common.client;

@FunctionalInterface
public interface HttpGetClient {

    <T> T get(String path, Class<T> responseType);
}
