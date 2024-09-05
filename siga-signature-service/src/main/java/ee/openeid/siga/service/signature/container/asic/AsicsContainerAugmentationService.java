package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.DataFile;
import org.digidoc4j.ServiceType;
import org.digidoc4j.Timestamp;
import org.digidoc4j.TimestampBuilder;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.impl.ServiceAccessListener;
import org.digidoc4j.impl.ServiceAccessScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ee.openeid.siga.common.event.SigaEventName.EventParam.REQUEST_URL;

@Slf4j
@Service
@Profile("datafileContainer")
public class AsicsContainerAugmentationService {
    private static final List<String> ALLOWED_ASICS_INNER_TYPES = List.of("ASICE", "BDOC", "DDOC");
    private final Configuration configuration;
    private final SigaEventLogger sigaEventLogger;

    @Autowired
    public AsicsContainerAugmentationService(Configuration configuration, SigaEventLogger sigaEventLogger) {
        this.configuration = configuration;
        this.sigaEventLogger = sigaEventLogger;
    }

    public Container augmentContainer(Container container) {
        // Container must contain at least 1 timestamp token
        validateTimestampsExist(container);

        // Container can only contain a DDOC, BDOC or ASiC-E container.
        validateInnerContainerType(container);

        // If all the checks passed, return ASiC-S with an additional timestamp
        try (ServiceAccessScope ignored = new ServiceAccessScope(createServiceAccessListener())) {
            Timestamp timestamp = TimestampBuilder.aTimestamp(container)
                    .invokeTimestamping();
            container.addTimestamp(timestamp);
        }
        return container;
    }

    private void validateInnerContainerType(Container container) {
        List<DataFile> dataFiles = container.getDataFiles();
        if (dataFiles.size() != 1) {
            throw new InvalidSessionDataException(
                    "Unable to augment. ASiC-S container must contain exactly 1 datafile, but contains " + dataFiles.size());
        }
        byte[] innerContainerBytes = dataFiles.get(0).getBytes();
        Container innerContainer;
        try {
            innerContainer = ContainerUtil.createContainer(innerContainerBytes, configuration);
        } catch (DigiDoc4JException e) {
            throw new InvalidSessionDataException("Unable to augment. The datafile in ASiC-S container must be a valid container.");
        }
        String innerContainerType = innerContainer.getType();
        if (!ALLOWED_ASICS_INNER_TYPES.contains(innerContainerType)) {
            throw new InvalidSessionDataException(
                    "Unable to augment. Invalid container type (" + innerContainerType
                    + ") found inside ASiC-S container. Allowed inner types are: "
                    + String.join(", ", ALLOWED_ASICS_INNER_TYPES));
        }
    }

    private static void validateTimestampsExist(Container container) {
        List<Timestamp> timestamps = container.getTimestamps();
        if (timestamps.isEmpty()) {
            throw new InvalidSessionDataException("Unable to augment. Container does not contain any timestamp tokens.");
        }
    }

    private ServiceAccessListener createServiceAccessListener() {
        return e -> {
            if (ServiceType.TSP == e.getServiceType()) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.TSA_REQUEST, REQUEST_URL, e.getServiceUrl()));
            } else {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.OCSP_REQUEST, REQUEST_URL, e.getServiceUrl()));
            }
        };
    }
}
