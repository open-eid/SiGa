package ee.openeid.siga.service.signature.mobileid;

import lombok.Data;

import java.security.cert.X509Certificate;

@Data
public class GetCertificateResponse {
    private X509Certificate certificate;
    private String status;
}
