package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.service.signature.test.TestUtil;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.Container.DocumentType;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AsicContainerAugmentationServiceTest {
    @InjectMocks
    private AsicContainerAugmentationService augmentationService;
    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);
    @Mock
    private AsiceContainerAugmentationService asiceAugmentationService;
    @Mock
    private AsicsContainerAugmentationService asicsAugmentationService;
    @Captor
    private ArgumentCaptor<Container> containerCaptor;

    @Test
    void asicsContainer_forwardedToAsicsAugmentationService() throws IOException {
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        byte[] containerBytes = TestUtil.getBytesFromContainer(container);

        augmentationService.augmentContainer(containerBytes, "test");

        verify(asicsAugmentationService, only()).augmentContainer(containerCaptor.capture());
        assertEquals("test.txt", containerCaptor.getValue().getDataFiles().get(0).getName());
        verify(asiceAugmentationService, never()).augmentContainer(any(), any(), any());
    }

    @Test
    void asiceContainer_forwardedToAsiceAugmentationService() throws IOException {
        Container container = ContainerBuilder.aContainer(DocumentType.ASICE)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        byte[] containerBytes = TestUtil.getBytesFromContainer(container);

        augmentationService.augmentContainer(containerBytes, "test");

        verify(asiceAugmentationService, only()).augmentContainer(eq(containerBytes), containerCaptor.capture(), eq("test"));
        assertEquals("test.txt", containerCaptor.getValue().getDataFiles().get(0).getName());
        verify(asicsAugmentationService, never()).augmentContainer(any());
    }
}
