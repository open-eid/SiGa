package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.ContainerType;
import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.DataFile;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContainerServiceImpl implements ContainerService {

    @Override
    public CreateContainerResponse createContainer(CreateContainerRequest request) {
        ContainerType containerType = getContainerType(request.getDataFiles());
        return null;
    }

    private ContainerType getContainerType(List<DataFile> dataFiles) {
        boolean isAttached = isAttachedContainerRequest(dataFiles);
        if (isAttached)
            return ContainerType.ATTACHED;
        boolean isDetached = isDetachedContainerRequest(dataFiles);
        if (isDetached)
            return ContainerType.DETACHED;
        throw new InvalidRequestException("Could not determine container type");
    }

    private boolean isAttachedContainerRequest(List<DataFile> dataFiles) {
        return dataFiles.stream().allMatch(dataFile ->
                StringUtils.isNotBlank(dataFile.getFileContent())
                        && StringUtils.isBlank(dataFile.getFileHashSha256())
                        && StringUtils.isBlank(dataFile.getFileHashSha512())
                        && dataFile.getFileSize() == null
                        && StringUtils.isNotBlank(dataFile.getFileName()));
    }

    private boolean isDetachedContainerRequest(List<DataFile> dataFiles) {
        return dataFiles.stream().allMatch(dataFile ->
                StringUtils.isBlank(dataFile.getFileContent())
                        && StringUtils.isNotBlank(dataFile.getFileHashSha256())
                        && StringUtils.isNotBlank(dataFile.getFileHashSha512())
                        && dataFile.getFileSize() > 0
                        && StringUtils.isNotBlank(dataFile.getFileName()));
    }
}
