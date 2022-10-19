package ee.openeid.siga.common.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CertificateStatus {
    private String status;
    private String documentNumber;
}
