package ee.openeid.siga.session.ignite;

import ee.openeid.siga.session.SessionServiceTest;
import ee.openeid.siga.session.spi.SessionStorage;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class IgniteSessionServiceTest extends SessionServiceTest {

    private static Ignite ignite;
    private static SessionStorage storage;

    @BeforeAll
    static void startIgnite() {
        System.setProperty("IGNITE_OVERRIDE_CONSISTENT_ID", "node00");
        ignite = Ignition.start("ignite-test-configuration.xml");
        storage = new IgniteSessionStorage(ignite);
    }

    @AfterAll
    static void stopIgnite() {
        if (ignite != null) {
            ignite.close();
        }
    }

    @Override
    protected SessionStorage storage() {
        return storage;
    }

    @Override
    protected void resetStorage() {
        ignite.cache(CacheName.CONTAINER_SESSION.name()).clear();
        ignite.cache(CacheName.SIGNATURE_SESSION.name()).clear();
        ignite.cache(CacheName.CERTIFICATE_SESSION.name()).clear();
    }
}
