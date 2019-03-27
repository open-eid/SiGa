package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.CertificateUtil;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.mobileid.model.mid.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import java.security.cert.X509Certificate;

@Slf4j
public class MidService extends WebServiceGatewaySupport {

    private static final String CONTEXT_PATH = "ee.openeid.siga.mobileid.model.mid";

    private final String serviceUrl;

    public MidService(String serviceUrl) {
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
            log.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

    public MobileSignHashResponse initMobileSignHash(MobileIdInformation mobileIdInformation, String hashType, String hash) {
        MobileSignHashRequest request = new MobileSignHashRequest();
        if (!(SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof SigaUserDetails)) {
            throw new TechnicalException("Invalid authentication principal object");
        }
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        request.setServiceName(sigaUserDetails.getServiceName());
        request.setLanguage(LanguageType.fromValue(mobileIdInformation.getLanguage()));
        request.setIDCode(mobileIdInformation.getPersonIdentifier());
        request.setPhoneNo(mobileIdInformation.getPhoneNo());
        request.setHash(hash);
        request.setHashType(HashType.fromValue(hashType));
        try {
            return (MobileSignHashResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        } catch (Exception e) {
            log.error("Invalid DigiDocService response:", e);
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
            log.error("Invalid DigiDocService response:", e);
            throw new ClientException("Unable to receive valid response from DigiDocService");
        }
    }

}
