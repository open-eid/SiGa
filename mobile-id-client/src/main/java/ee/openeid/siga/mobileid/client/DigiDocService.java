package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificate;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificateResponse;
import ee.openeid.siga.mobileid.model.dds.ReturnCertDataType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.SoapFaultClientException;

@Slf4j
public class DigiDocService extends WebServiceGatewaySupport {

    private static final String CONTEXT_PATH = "ee.openeid.siga.mobileid.model.dds";

    private final String serviceUrl;

    public DigiDocService(String serviceUrl) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(CONTEXT_PATH);
        setMarshaller(marshaller);
        setUnmarshaller(marshaller);
        this.serviceUrl = serviceUrl;
    }

    @SigaEventLog(eventName = SigaEventName.DDS_GET_MOBILE_CERTIFICATE, logParameters = {@Param(name = "person_identifier", index = 0), @Param(name = "country", index = 1), @Param(name = "phone_nr", index = 2)}, logReturnObject = {@XPath(name = "sign_cert_status", xpath = "signCertStatus"), @XPath(name = "dds_response_code", xpath = "authCertStatus")})
    public GetMobileCertificateResponse getMobileCertificate(String idCode, String country, String phoneNr) {
        GetMobileCertificate request = new GetMobileCertificate();
        request.setIDCode(idCode);
        request.setCountry(country);
        request.setPhoneNo(phoneNr);
        request.setReturnCertData(ReturnCertDataType.SIGN);
        try {
            GetMobileCertificateResponse response = (GetMobileCertificateResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
            return response;
        } catch (SoapFaultClientException e) {
            throw new ClientException("DigiDocService error. SOAP fault code: " + e.getFaultStringOrReason());
        } catch (Exception e) {
            log.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

}
