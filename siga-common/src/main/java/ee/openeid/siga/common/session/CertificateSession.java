package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.RelyingPartyInfo;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class CertificateSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private RelyingPartyInfo relyingPartyInfo;
    private String sessionCode;
    private String documentNumber;
    @Builder.Default
    private SessionStatus sessionStatus = SessionStatus.builder().build();

    public void setPollingStatus(ProcessingStatus status) {
        sessionStatus.setProcessingStatus(status);
    }
}
