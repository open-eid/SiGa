package ee.openeid.siga;

import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.service.signature.AttachedDataFileContainerService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.DataFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AttachedDataFileContainerController {

    private AttachedDataFileContainerService containerService;
//    private AttachedDataFileContainerValidationService validationService;
//    private AttachedDataFileContainerSigningService signingService;

    @SigaEventLog(eventName = SigaEventName.CREATE_CONTAINER, logParameters = {@Param(index = 0, fields = {@XPath(name = "no_of_datafiles", xpath = "helper:size(dataFiles)")})}, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @RequestMapping(value = "/containers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateContainerResponse createContainer(@RequestBody CreateContainerRequest createContainerRequest) {
        List<DataFile> dataFiles = createContainerRequest.getDataFiles();
        RequestValidator.validateDataFiles(dataFiles);

        String sessionId = containerService.createContainer(RequestTransformer.transformDataFiles(dataFiles));
        CreateContainerResponse response = new CreateContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @Autowired
    public void setContainerService(AttachedDataFileContainerService containerService) {
        this.containerService = containerService;
    }
}
