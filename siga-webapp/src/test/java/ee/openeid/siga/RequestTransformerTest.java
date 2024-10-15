package ee.openeid.siga;

import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.Timestamp;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestTransformerTest {

    @Test
    void transformValidSignatureToDetails_validDateFormats() throws Exception {
        Path documentPath = Paths.get(new ClassPathResource("datafile.asice").getURI());
        InputStream inputStream = new ByteArrayInputStream(Files.readAllBytes(documentPath));
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).fromStream(inputStream).build();

        GetContainerSignatureDetailsResponse response = RequestTransformer.transformSignatureToDetails(container.getSignatures().get(0));
        assertEquals("2018-11-23T12:24:04Z", response.getClaimedSigningTime());
        assertEquals("2018-11-23T12:24:05Z", response.getOcspResponseCreationTime());
        assertEquals("2018-11-23T12:24:04Z", response.getTimeStampCreationTime());
        assertEquals("2018-11-23T12:24:04Z", response.getTrustedSigningTime());
    }

    @Test
    void transformTimestampsForResponse_correctDetails() throws Exception {
        Path documentPath = Paths.get(new ClassPathResource("asics_containing_ddoc_and_timestamp.asics").getURI());
        InputStream inputStream = new ByteArrayInputStream(Files.readAllBytes(documentPath));
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).fromStream(inputStream).build();

        List<Timestamp> timestamps = RequestTransformer.transformTimestampsForResponse(container.getTimestamps());
        assertEquals(1, timestamps.size());
        assertEquals("T-519156403B8A19A11569455AA86FD01165C0209F55D6DB244333C001313AA5C9", timestamps.get(0).getId());
        assertEquals("2024-09-09T12:13:34Z", timestamps.get(0).getCreationTime());
    }

}
