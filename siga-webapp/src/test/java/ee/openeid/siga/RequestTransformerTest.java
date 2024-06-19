package ee.openeid.siga;

import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
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

}
