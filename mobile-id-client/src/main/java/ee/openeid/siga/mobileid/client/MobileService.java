package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.mobileid.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import java.security.cert.X509Certificate;

public class MobileService extends WebServiceGatewaySupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileService.class);

    private static final String CONTEXT_PATH = "ee.openeid.siga.mobileid.model";

    private final String serviceUrl;

    public MobileService(String serviceUrl) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(CONTEXT_PATH);
        setMarshaller(marshaller);
        setUnmarshaller(marshaller);
        this.serviceUrl = serviceUrl;
    }

    public X509Certificate getMobileCertificate(String idCode, String country) {
        GetMobileCertByIDCodeRequest request = new GetMobileCertByIDCodeRequest();
        request.setIDCode(idCode);
        request.setCountry(country);
        request.setReturnCertData(ReturnCertDataType.SIGN);
        try {
            GetMobileCertByIDCodeResponse response = (GetMobileCertByIDCodeResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
            return CertificateUtil.createX509Certificate(response.getSignCertData());
        } catch (Exception e) {
            LOGGER.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

    public MobileSignHashResponse initMobileSignHash(MobileIdInformation mobileIdInformation, String hashType, String hash) {
        MobileSignHashRequest request = new MobileSignHashRequest();
        request.setServiceName(mobileIdInformation.getServiceName());
        request.setLanguage(LanguageType.fromValue(mobileIdInformation.getLanguage()));
        request.setIDCode(mobileIdInformation.getPersonIdentifier());
        request.setPhoneNo(mobileIdInformation.getPhoneNo());
        request.setHash(hash);
        request.setHashType(HashType.fromValue(hashType));
        try {
            return (MobileSignHashResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        } catch (Exception e) {
            LOGGER.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

    public GetMobileSignHashStatusResponse getMobileSignHashStatus(String sessCode) {
        GetMobileSignHashStatusRequest request = new GetMobileSignHashStatusRequest();
        request.setSesscode(sessCode);
        request.setWaitSignature(false);
        try {
            return (GetMobileSignHashStatusResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        } catch (Exception e) {
            LOGGER.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

}
