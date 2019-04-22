package ee.openeid.siga.client.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessingStatus {

    private boolean containerReadyForDownload;
    private String errorMessage;
    private String requestMethod;
    private String apiEndpoint;
    private Object apiRequestObject;
    private Object apiResponseObject;
}
