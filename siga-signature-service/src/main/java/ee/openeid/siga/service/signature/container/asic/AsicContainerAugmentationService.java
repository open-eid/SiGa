package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Profile("datafileContainer")
public class AsicContainerAugmentationService {
    private static final List<String> ASICE_FILE_TYPES = List.of("ASICE", "BDOC");
    private static final String ASICS_FILE_TYPE = "ASICS";
    private final Configuration configuration;
    private final AsiceContainerAugmentationService asiceService;
    private final AsicsContainerAugmentationService asicsService;

    @Autowired
    public AsicContainerAugmentationService(
            Configuration configuration,
            AsiceContainerAugmentationService asiceService,
            AsicsContainerAugmentationService asicsService) {
        this.configuration = configuration;
        this.asiceService = asiceService;
        this.asicsService = asicsService;
    }

    public Container augmentContainer(byte[] containerBytes, String containerName) {
        Container container = ContainerUtil.createContainer(containerBytes, configuration);
        String containerType = container.getType();

        if (ASICE_FILE_TYPES.contains(containerType)) {
            return asiceService.augmentContainer(containerBytes, container, containerName);
        } else if (ASICS_FILE_TYPE.equals(containerType)) {
            return asicsService.augmentContainer(container);
        } else {
            throw new InvalidContainerException("Invalid container type for augmentation: " + containerType);
        }
    }
}
