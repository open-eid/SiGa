package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificate;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificateResponse;
import ee.openeid.siga.mobileid.model.dds.ReturnCertDataType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import java.security.cert.X509Certificate;

@Slf4j
public class DdsService extends WebServiceGatewaySupport {

    private static final String CONTEXT_PATH = "ee.openeid.siga.mobileid.model.dds";

    private final String serviceUrl;

    public DdsService(String serviceUrl) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(CONTEXT_PATH);
        setMarshaller(marshaller);
        setUnmarshaller(marshaller);
        this.serviceUrl = serviceUrl;
    }

    public X509Certificate getMobileCertificate(String idCode, String country, String phoneNr) {
        GetMobileCertificate request = new GetMobileCertificate();
        request.setIDCode(idCode);
        request.setCountry(country);
        request.setPhoneNo(phoneNr);
        request.setReturnCertData(ReturnCertDataType.SIGN);
        try {
            GetMobileCertificateResponse response = (GetMobileCertificateResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
            return CertificateUtil.createX509Certificate(response.getSignCertData().getBytes());
        } catch (Exception e) {
            log.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

}
