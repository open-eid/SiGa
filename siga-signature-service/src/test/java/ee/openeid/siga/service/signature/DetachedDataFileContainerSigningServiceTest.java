package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.SigningType;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.mobileid.client.MobileService;
import ee.openeid.siga.mobileid.model.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.ProcessStatusType;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import org.digidoc4j.*;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;

import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.createSignatureParameters;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class DetachedDataFileContainerSigningServiceTest {
    private final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());

    private static final String EXPECTED_DATATOSIGN_PREFIX = "<ds:SignedInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512\"></ds:SignatureMethod><ds:Reference Id=\"r-id-1\" Type=\"\" URI=\"test.txt\"><ds:DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha512\"></ds:DigestMethod><ds:DigestValue>gRKArS6jBsPLF1VP7aQ8VZ7BA5QA66hj/ntmNcxONZG5899w2VFHg9psyEH4Scg7rPSJQEYf65BGAscMztSXsA==</ds:DigestValue></ds:Reference><ds:Reference Type=\"http://uri.etsi.org/01903#SignedProperties\"";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @InjectMocks
    private DetachedDataFileContainerSigningService signingService;

    @Mock
    private MobileService mobileService;

    @Mock
    private SessionService sessionService;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createSessionHolder());
    }

    @Test
    public void createDataToSignSuccessful() {
        DataToSign dataToSign = signingService.createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
        Assert.assertEquals(DigestAlgorithm.SHA512, dataToSign.getDigestAlgorithm());
        Assert.assertTrue(new String(dataToSign.getDataToSign()).startsWith(EXPECTED_DATATOSIGN_PREFIX));
    }

    @Test
    public void invalidContainerId() {
        exceptionRule.expect(TechnicalException.class);
        exceptionRule.expectMessage("Unable to parse session");
        signingService.createDataToSign("random-container-id", createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
    }

    @Test
    public void noDataFilesInSession() throws IOException, URISyntaxException {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to create signature. Data files must be added to container");
        DetachedDataFileContainerSessionHolder sessionHolder = RequestUtil.createSessionHolder();
        sessionHolder.getDataFiles().clear();

        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
        signingService.createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
    }

    @Test
    public void onlyRequiredSignatureParameters() {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        signatureParameters.setCountry(null);
        signatureParameters.setRoles(null);
        signatureParameters.setPostalCode(null);
        signatureParameters.setStateOrProvince(null);
        signatureParameters.setCity(null);
        DataToSign dataToSign = signingService.createDataToSign(CONTAINER_ID, signatureParameters);
        Assert.assertEquals(DigestAlgorithm.SHA512, dataToSign.getDigestAlgorithm());
        Assert.assertTrue(new String(dataToSign.getDataToSign()).startsWith(EXPECTED_DATATOSIGN_PREFIX));
    }

    @Test
    public void signAndValidateSignature() {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        signingService.setConfiguration(Configuration.of(Configuration.Mode.TEST));
        signatureParameters.setDigestAlgorithm(DigestAlgorithm.SHA512);
        DataToSign dataToSign = signingService.createDataToSign(CONTAINER_ID, signatureParameters);
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        Signature signature = dataToSign.finalize(signatureRaw);
        Assert.assertEquals("Tallinn", signature.getCity());
        Assert.assertEquals("34234", signature.getPostalCode());
        Assert.assertEquals("Harjumaa", signature.getStateOrProvince());
        Assert.assertEquals("Estonia", signature.getCountryName());
        Assert.assertEquals("Engineer", signature.getSignerRoles().get(0));
        Assert.assertTrue(signature.validateSignature().isValid());
    }

    @Test
    public void finalizeAndValidateSignature() {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        signingService.setConfiguration(Configuration.of(Configuration.Mode.TEST));
        DataToSign dataToSign = signingService.createDataToSign(CONTAINER_ID, signatureParameters);
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));
        String result = signingService.finalizeSigning(CONTAINER_ID, base64EncodedSignature);
        Assert.assertEquals("OK", result);
    }

    @Test
    public void noDataToSignInSession() {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to finalize signature. Invalid session found");
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, Base64.getDecoder().decode("kZLQdTYDtWjSbmFlM3RO+vAfygvKDKfQHQkYrDflIDj98r28vlSTMkewVDzlsuzeIY6G+Skr1jmpQmuDr7usJQ=="));
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));
        signingService.finalizeSigning(CONTAINER_ID, base64EncodedSignature);
    }

    @Test
    public void successfulMobileIdSigning() {
        Mockito.when(mobileService.getMobileCertificate(any(), any())).thenReturn(pkcs12Esteid2018SignatureToken.getCertificate());
        Mockito.when(mobileService.initMobileSignHash(any(), any(), any())).thenReturn(createMobileSignHashResponse());
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        MobileIdInformation mobileIdInformation = RequestUtil.createMobileInformation();
        signingService.startMobileIdSigning(CONTAINER_ID, mobileIdInformation, signatureParameters);
    }

    @Test
    public void invalidMobileSignHashStatus() {
        exceptionRule.expect(IllegalStateException.class);
        exceptionRule.expectMessage("Invalid DigiDocService response");
        MobileSignHashResponse mobileSignHashResponse = createMobileSignHashResponse();
        mobileSignHashResponse.setStatus("Random");
        Mockito.when(mobileService.getMobileCertificate(any(), any())).thenReturn(pkcs12Esteid2018SignatureToken.getCertificate());
        Mockito.when(mobileService.initMobileSignHash(any(), any(), any())).thenReturn(mobileSignHashResponse);
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        MobileIdInformation mobileIdInformation = RequestUtil.createMobileInformation();
        signingService.startMobileIdSigning(CONTAINER_ID, mobileIdInformation, signatureParameters);
    }

    @Test
    public void successfulMobileIdSignatureProcessing() throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        signingService.setConfiguration(Configuration.of(Configuration.Mode.TEST));
        DataToSign dataToSign = signingService.createDataToSign(CONTAINER_ID, signatureParameters);

        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        DetachedDataFileContainerSessionHolder session = RequestUtil.createSessionHolder();
        session.setSessionCode("2342384932");
        session.setDataToSign(dataToSign);
        session.setSigningType(SigningType.MOBILE_ID);

        GetMobileSignHashStatusResponse getMobileSignHashStatusResponse = new GetMobileSignHashStatusResponse();
        getMobileSignHashStatusResponse.setSignature(signatureRaw);
        getMobileSignHashStatusResponse.setStatus(ProcessStatusType.SIGNATURE);

        Mockito.when(mobileService.getMobileSignHashStatus(any())).thenReturn(getMobileSignHashStatusResponse);
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(session);

        signingService.processMobileStatus(CONTAINER_ID);
    }

    @Test
    public void noSessionFoundMobileSigning() {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to finalize signature. Invalid session found");
        signingService.processMobileStatus(CONTAINER_ID);
    }

    private MobileSignHashResponse createMobileSignHashResponse() {
        MobileSignHashResponse mobileSignHashResponse = new MobileSignHashResponse();
        mobileSignHashResponse.setStatus("OK");
        mobileSignHashResponse.setChallengeID("2331");
        mobileSignHashResponse.setSesscode("3223423423424");
        return mobileSignHashResponse;
    }
}
