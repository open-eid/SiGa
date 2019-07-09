package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.Result;
import ee.openeid.siga.common.SigningChallenge;
import ee.openeid.siga.common.SmartIdInformation;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificateResponse;
import ee.openeid.siga.mobileid.model.mid.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.mid.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import ee.openeid.siga.service.signature.configuration.SmartIdServiceConfigurationProperties;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.util.io.pem.PemWriter;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Base64;

import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.createSignatureParameters;
import static org.mockito.ArgumentMatchers.any;

public abstract class ContainerSigningServiceTest {

    @Mock
    private MobileIdService mobileIdService;

    @Mock
    private SmartIdServiceConfigurationProperties smartIdProperties;

    @Mock
    private DigiDocService digiDocService;

    protected final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());

    protected void assertCreateDataToSignSuccessful() {
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate())).getDataToSign();
        Assert.assertEquals(DigestAlgorithm.SHA512, dataToSign.getDigestAlgorithm());
        Assert.assertTrue(new String(dataToSign.getDataToSign()).startsWith(getExpectedDataToSignPrefix()));
    }

    protected void invalidContainerId() {
        getSigningService().createDataToSign("random-container-id", createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
    }

    protected void onlyRequiredSignatureParameters() {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        signatureParameters.setCountry(null);
        signatureParameters.setRoles(null);
        signatureParameters.setPostalCode(null);
        signatureParameters.setStateOrProvince(null);
        signatureParameters.setCity(null);
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        Assert.assertEquals(DigestAlgorithm.SHA512, dataToSign.getDigestAlgorithm());
        Assert.assertTrue(new String(dataToSign.getDataToSign()).startsWith(getExpectedDataToSignPrefix()));
    }

    protected void signAndValidateSignature() {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        setSigningServiceParameters();
        signatureParameters.setDigestAlgorithm(DigestAlgorithm.SHA512);
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        Signature signature = dataToSign.finalize(signatureRaw);
        Assert.assertEquals("Tallinn", signature.getCity());
        Assert.assertEquals("34234", signature.getPostalCode());
        Assert.assertEquals("Harjumaa", signature.getStateOrProvince());
        Assert.assertEquals("Estonia", signature.getCountryName());
        Assert.assertEquals("Engineer", signature.getSignerRoles().get(0));
        Assert.assertTrue(signature.validateSignature().isValid());
    }

    protected void finalizeAndValidateSignature() throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        setSigningServiceParameters();
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));
        mockRemoteSessionHolder(dataToSign);
        Result result = getSigningService().finalizeSigning(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId(), base64EncodedSignature);
        Assert.assertEquals(Result.OK, result);
    }

    protected void noDataToSignInSession() {
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, Base64.getDecoder().decode("kZLQdTYDtWjSbmFlM3RO+vAfygvKDKfQHQkYrDflIDj98r28vlSTMkewVDzlsuzeIY6G+Skr1jmpQmuDr7usJQ=="));
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));
        getSigningService().finalizeSigning(CONTAINER_ID, "someUnknownSignatureId", base64EncodedSignature);
    }

    protected void noDataToSignInSessionForSignatureId() throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        setSigningServiceParameters();
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));

        mockRemoteSessionHolder(dataToSign);
        try {
            getSigningService().finalizeSigning(CONTAINER_ID, "someUnknownSignatureId", base64EncodedSignature);
        } catch (InvalidSessionDataException e) {
            Result result = getSigningService().finalizeSigning(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId(), base64EncodedSignature);
            Assert.assertEquals(Result.OK, result);
            throw e;
        }
    }

    protected void successfulMobileIdSigning() throws IOException {
        GetMobileCertificateResponse mobileCertificateResponse = createMobileCertificateResponse();
        Mockito.when(digiDocService.getMobileCertificate(any(), any())).thenReturn(mobileCertificateResponse);
        Mockito.when(mobileIdService.initMobileSignHash(any(), any(), any())).thenReturn(createMobileSignHashResponse());
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        MobileIdInformation mobileIdInformation = RequestUtil.createMobileInformation();
        getSigningService().startMobileIdSigning(CONTAINER_ID, mobileIdInformation, signatureParameters);
    }

    protected void invalidMobileSignHashStatus() throws IOException {
        MobileSignHashResponse mobileSignHashResponse = createMobileSignHashResponse();
        mobileSignHashResponse.setStatus("Random");
        GetMobileCertificateResponse mobileCertificateResponse = createMobileCertificateResponse();
        Mockito.when(digiDocService.getMobileCertificate(any(), any())).thenReturn(mobileCertificateResponse);
        Mockito.when(mobileIdService.initMobileSignHash(any(), any(), any())).thenReturn(mobileSignHashResponse);
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        MobileIdInformation mobileIdInformation = RequestUtil.createMobileInformation();
        getSigningService().startMobileIdSigning(CONTAINER_ID, mobileIdInformation, signatureParameters);
    }

    protected void successfulMobileIdSignatureProcessing() throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        setSigningServiceParameters();
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();

        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());

        GetMobileSignHashStatusResponse getMobileSignHashStatusResponse = new GetMobileSignHashStatusResponse();
        getMobileSignHashStatusResponse.setSignature(signatureRaw);
        getMobileSignHashStatusResponse.setStatus(ProcessStatusType.SIGNATURE);

        Mockito.when(mobileIdService.getMobileSignHashStatus(any())).thenReturn(getMobileSignHashStatusResponse);
        mockMobileIdSessionHolder(dataToSign);
        String status = getSigningService().processMobileStatus(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId());
        Assert.assertEquals("SIGNATURE", status);
    }

    protected void successfulSmartIdSigning() {
        Mockito.when(smartIdProperties.getUrl()).thenReturn("https://sid.demo.sk.ee/smart-id-rp/v1/");
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        SigningChallenge signingChallenge = getSigningService().startSmartIdSigning(CONTAINER_ID, smartIdInformation, signatureParameters);
        Assert.assertNotNull(signingChallenge.getChallengeId());
        Assert.assertNotNull(signingChallenge.getGeneratedSignatureId());
    }

    protected void successfulSmartIdSignatureProcessing(SessionService sessionService) throws IOException, URISyntaxException {
        Session sessionHolder = getSessionHolder();
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        Mockito.when(smartIdProperties.getUrl()).thenReturn("https://sid.demo.sk.ee/smart-id-rp/v1/");
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();

        SigningChallenge signingChallenge = getSigningService().startSmartIdSigning(CONTAINER_ID, smartIdInformation, signatureParameters);
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
        String result = getSigningService().processSmartIdStatus(CONTAINER_ID, signingChallenge.getGeneratedSignatureId(), smartIdInformation);
        Assert.assertEquals("COMPLETE", result);
    }


    protected abstract ContainerSigningService getSigningService();

    protected abstract String getExpectedDataToSignPrefix();

    protected abstract void setSigningServiceParameters();

    protected abstract void mockRemoteSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract void mockMobileIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract Session getSessionHolder() throws IOException, URISyntaxException;

    private MobileSignHashResponse createMobileSignHashResponse() {
        MobileSignHashResponse mobileSignHashResponse = new MobileSignHashResponse();
        mobileSignHashResponse.setStatus(Result.OK.name());
        mobileSignHashResponse.setChallengeID("2331");
        mobileSignHashResponse.setSesscode("3223423423424");
        return mobileSignHashResponse;
    }

    private GetMobileCertificateResponse createMobileCertificateResponse() throws IOException {
        StringWriter writer = new StringWriter();
        GetMobileCertificateResponse mobileCertificateResponse = new GetMobileCertificateResponse();
        try (PemWriter pemWriter = new PemWriter(writer)) {
            pemWriter.writeObject(new JcaMiscPEMGenerator(pkcs12Esteid2018SignatureToken.getCertificate(), null));
            pemWriter.flush();
            String certInPemFormat = writer.toString();
            mobileCertificateResponse.setSignCertData(certInPemFormat);
        }
        return mobileCertificateResponse;
    }
}
