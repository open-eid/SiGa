package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.event.LogParam;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.TechnicalException;
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

    @SigaEventLog(eventName = SigaEventName.DDS_GET_MOBILE_CERTIFICATE,
            logReturnObject = {@XPath(name = "sign_cert_status", xpath = "signCertStatus"), @XPath(name = "dds_response_code", xpath = "authCertStatus")},
            logStaticParameters = {@LogParam(name = SigaEventName.EventParam.REQUEST_URL, value = "${siga.dds.url-v1}")})
    public GetMobileCertificateResponse getMobileCertificate(String idCode, String phoneNr) {
        GetMobileCertificate request = new GetMobileCertificate();
        request.setIDCode(idCode);
        request.setPhoneNo(phoneNr);
        request.setReturnCertData(ReturnCertDataType.SIGN);
        try {
            return (GetMobileCertificateResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        } catch (SoapFaultClientException e) {
            throw SoapFaultHandlingUtil.handleSoapFaultClientException(e);
        } catch (Exception e) {
            log.error("Invalid DigiDocService response:", e);
            throw new TechnicalException("Unable to receive valid response from DigiDocService", e);
        }
    }

}
