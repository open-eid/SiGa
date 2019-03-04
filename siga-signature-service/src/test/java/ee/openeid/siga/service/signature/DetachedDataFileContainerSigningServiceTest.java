package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.exception.NoDataFileException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
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

import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.createSignatureParameters;

@RunWith(MockitoJUnitRunner.class)
public class DetachedDataFileContainerSigningServiceTest {
    private final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());

    private static final String EXPECTED_DATATOSIGN_PREFIX = "<ds:SignedInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512\"></ds:SignatureMethod><ds:Reference Id=\"r-id-1\" Type=\"\" URI=\"test.txt\"><ds:DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha512\"></ds:DigestMethod><ds:DigestValue>YXNkamFvc2Rhc2Rhc2Rhc2Rhc2RzZGFkamFzcD0=</ds:DigestValue></ds:Reference><ds:Reference Type=\"http://uri.etsi.org/01903#SignedProperties\"";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @InjectMocks
    private DetachedDataFileContainerSigningService signingService;

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
        exceptionRule.expect(NoDataFileException.class);
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

}
