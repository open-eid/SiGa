package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.mobileid.model.mid.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.security.cert.X509Certificate;

@Slf4j
public class MobileIdService extends WebServiceGatewaySupport {

    private static final String CONTEXT_PATH = "ee.openeid.siga.mobileid.model.mid";

    private final String serviceUrl;

    public MobileIdService(String serviceUrl) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(CONTEXT_PATH);
        setMarshaller(marshaller);
        setUnmarshaller(marshaller);
        this.serviceUrl = serviceUrl;
    }

    public X509Certificate getMobileX509Certificate(String idCode, String country) {
        GetMobileCertByIDCodeResponse response = getMobileCertificate(idCode, country);
        return CertificateUtil.createX509Certificate(response.getSignCertData());
    }

    @SigaEventLog(eventName = SigaEventName.DDS_GET_MOBILE_CERTIFICATE, logParameters = {@Param(name = "person_identifier", index = 0), @Param(name = "country", index = 1)}, logReturnObject = {@XPath(name = "sign_cert_status", xpath = "signCertStatus"), @XPath(name = "dds_response_code", xpath = "authCertStatus")})
    public GetMobileCertByIDCodeResponse getMobileCertificate(String idCode, String country) {
        GetMobileCertByIDCodeRequest request = new GetMobileCertByIDCodeRequest();
        request.setIDCode(idCode);
        request.setCountry(country);
        request.setReturnCertData(ReturnCertDataType.SIGN);
        try {
            return (GetMobileCertByIDCodeResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        } catch (SoapFaultClientException e) {
            throw new ClientException("DigiDocService error. SOAP fault code: " + e.getFaultStringOrReason());
        } catch (Exception e) {
            log.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

    @SigaEventLog(eventName = SigaEventName.DDS_MOBILE_SIGN_HASH, logParameters = {@Param(index = 0, fields = {@XPath(name = "person_identifier", xpath = "personIdentifier")})}, logReturnObject = {@XPath(name = "dds_session_id", xpath = "sesscode"), @XPath(name = "dds_response_code", xpath = "status")})
    public MobileSignHashResponse initMobileSignHash(MobileIdInformation mobileIdInformation, String hashType, String hash) {
        MobileSignHashRequest request = new MobileSignHashRequest();
        request.setServiceName(mobileIdInformation.getRelyingPartyName());
        request.setLanguage(LanguageType.fromValue(mobileIdInformation.getLanguage()));
        request.setIDCode(mobileIdInformation.getPersonIdentifier());
        request.setPhoneNo(mobileIdInformation.getPhoneNo());
        request.setHash(hash);
        request.setHashType(HashType.fromValue(hashType));
        try {
            MobileSignHashResponse response = (MobileSignHashResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
            return response;
        } catch (SoapFaultClientException e) {
            throw new ClientException("DigiDocService error. SOAP fault code: " + e.getFaultStringOrReason());
        } catch (Exception e) {
            log.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

    @SigaEventLog(eventName = SigaEventName.DDS_GET_MOBILE_SIGN_HASH_STATUS, logParameters = {@Param(name = "dds_session_id", index = 0)}, logReturnObject = {@XPath(name = "dds_session_id", xpath = "sesscode"), @XPath(name = "dds_response_code", xpath = "status")})
    public GetMobileSignHashStatusResponse getMobileSignHashStatus(String sessCode) {
        GetMobileSignHashStatusRequest request = new GetMobileSignHashStatusRequest();
        request.setSesscode(sessCode);
        request.setWaitSignature(false);
        try {
            return (GetMobileSignHashStatusResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        } catch (SoapFaultClientException e) {
            throw new ClientException("DigiDocService error. SOAP fault code: " + e.getFaultStringOrReason());
        } catch (Exception e) {
            log.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

}
