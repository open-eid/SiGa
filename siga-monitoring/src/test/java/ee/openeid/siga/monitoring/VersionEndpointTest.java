package ee.openeid.siga.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static ee.openeid.siga.monitoring.ApplicationInfoConstants.MANIFEST_PARAM_VERSION;
import static ee.openeid.siga.monitoring.ApplicationInfoConstants.NOT_AVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class VersionEndpointTest {

    private static final String TEST_VERSION = "TEST_VERSION";

    @Mock
    private ManifestReader manifestReader;

    @Test
    public void testVersionMissingInManifestFile() {
        VersionEndpoint versionEndpoint = new VersionEndpoint(manifestReader);

        Map<String, Object> result = versionEndpoint.version();

        assertEquals(
                Map.of(VersionEndpoint.RESPONSE_PARAM_VERSION, NOT_AVAILABLE),
                result
        );
        Mockito.verify(manifestReader).read(MANIFEST_PARAM_VERSION);
        Mockito.verifyNoMoreInteractions(manifestReader);
    }

    @Test
    public void testVersionFoundInManifestFile() {
        Mockito.doReturn(TEST_VERSION).when(manifestReader).read(Mockito.anyString());
        VersionEndpoint versionEndpoint = new VersionEndpoint(manifestReader);

        Map<String, Object> result = versionEndpoint.version();

        assertEquals(
                Map.of(VersionEndpoint.RESPONSE_PARAM_VERSION, TEST_VERSION),
                result
        );
        Mockito.verify(manifestReader).read(MANIFEST_PARAM_VERSION);
        Mockito.verifyNoMoreInteractions(manifestReader);
    }

}
