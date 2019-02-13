package ee.openeid.siga.mobileid.client;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;

import ee.openeid.siga.mobileid.model.GetMobileCertByIDCodeRequest;
import ee.openeid.siga.mobileid.model.GetMobileCertByIDCodeResponse;
import ee.openeid.siga.mobileid.model.GetMobileSignHashStatusRequest;
import ee.openeid.siga.mobileid.model.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.HashType;
import ee.openeid.siga.mobileid.model.LanguageType;
import ee.openeid.siga.mobileid.model.MobileSignHashRequest;
import ee.openeid.siga.mobileid.model.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.ReturnCertDataType;

public class MobileService extends WebServiceGatewaySupport {

    private final String serviceUrl;

    public MobileService(String serviceUrl) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("com.nortal.sample.mobileid.model");
        setMarshaller(marshaller);
        setUnmarshaller(marshaller);
        this.serviceUrl = serviceUrl;
    }

    public X509Certificate getMobileCertificate(String idCode) {
        GetMobileCertByIDCodeRequest request = new GetMobileCertByIDCodeRequest();
        request.setIDCode(idCode);
        request.setCountry("EE");
        request.setReturnCertData(ReturnCertDataType.SIGN);

        GetMobileCertByIDCodeResponse response = (GetMobileCertByIDCodeResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(response.getSignCertData()));
        } catch (CertificateException e) {
            throw new RuntimeException("Error constructing certificate object from bytes");
        }
    }

    public MobileSignHashResponse initMobileSignHash(String idCode, String phoneNo, String hashType, String hash) {
        MobileSignHashRequest request = new MobileSignHashRequest();
        request.setServiceName("Testimine");
        request.setLanguage(LanguageType.EST);
        request.setIDCode(idCode);
        request.setPhoneNo(phoneNo);
        request.setHash(hash);
        request.setHashType(HashType.fromValue(hashType));

        return (MobileSignHashResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
    }

    public GetMobileSignHashStatusResponse getMobileSignHashStatus(String sessCode) {
        GetMobileSignHashStatusRequest request = new GetMobileSignHashStatusRequest();
        request.setSesscode(sessCode);
        request.setWaitSignature(false);

        return (GetMobileSignHashStatusResponse) getWebServiceTemplate().marshalSendAndReceive(serviceUrl, request);
    }

}
