package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.mobileid.model.*;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class MobileService extends WebServiceGatewaySupport {
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
        GetMobileCertByIDCodeResponse response = (GetMobileCertByIDCodeResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(response.getSignCertData()));
        } catch (CertificateException e) {
            throw new RuntimeException("Error constructing certificate object from bytes");
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

        return  (MobileSignHashResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
    }

    public GetMobileSignHashStatusResponse getMobileSignHashStatus(String sessCode) {
        GetMobileSignHashStatusRequest request = new GetMobileSignHashStatusRequest();
        request.setSesscode(sessCode);
        request.setWaitSignature(false);

        return (GetMobileSignHashStatusResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
    }

}
