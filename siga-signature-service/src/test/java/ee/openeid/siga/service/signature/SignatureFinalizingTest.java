package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.SignatureCreationException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.DataToSignHolder;
import ee.openeid.siga.common.session.HashcodeContainerSessionHolder;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.model.DSSException;
import org.apache.commons.lang3.tuple.Pair;
import org.digidoc4j.Configuration;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Optional;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEvent.EventResultType.SUCCESS;
import static ee.openeid.siga.common.event.SigaEvent.EventType.FINISH;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.REQUEST_URL;
import static ee.openeid.siga.common.event.SigaEventName.*;
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SignatureFinalizingTest {
    private final PKCS12SignatureToken VALID_PKCS12_Esteid2018 = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());
    private final PKCS12SignatureToken REVOKED_PKCS12_Esteid2018 = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());
    private final PKCS12SignatureToken UNKNOWN_PKCS12_Esteid2018 = new PKCS12SignatureToken("src/test/resources/p12/unknown_issuer_DEV_of_ESTEID2018.p12", "1234".toCharArray());
    private final PKCS12SignatureToken EXPIRED_PKCS12_Esteid2011 = new PKCS12SignatureToken("src/test/resources/p12/expired_signer_ESTEID-SK 2011.p12", "test".toCharArray());

    @InjectMocks
    private HashcodeContainerSigningService signingService;

    @Mock
    private SessionService sessionService;

    @Spy
    private Configuration configuration = new Configuration(Configuration.Mode.TEST);

    @Spy
    private SigaEventLogger sigaEventLogger;

    @Mock
    private Authentication authentication;
    @Mock
    private SecurityContext securityContext;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder().build());
        SecurityContextHolder.setContext(securityContext);
        configuration.setPreferAiaOcsp(true);
        signingService.setConfiguration(configuration);
        when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createHashcodeSessionHolder());
    }

    @Test
    public void shouldRequest_TSA_OCSP_WithSignatureProfile_LT_AndPreferAiaOcspFalse() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);
        Result result = signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        sigaEventLogger.logEvents();
        assertEquals(Result.OK, result);
        assertTSAOCSPEvents("http://demo.sk.ee/tsa", "http://demo.sk.ee/ocsp");
    }


    @Test
    public void shouldRequest_TSA_OCSP_WithSignatureProfile_LT_TM_AndPreferAiaOcspFalse() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT_TM);
        Result result = signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        sigaEventLogger.logEvents();
        assertEquals(Result.OK, result);
        assertTSAOCSPEvents(null, "http://demo.sk.ee/ocsp");
    }


    @Test
    public void shouldRequestOnly_OCSP_WithSignatureProfile_LT_TM_AndPreferAiaOcspTrue() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(true);
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT_TM);
        Result result = signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        sigaEventLogger.logEvents();
        assertEquals(Result.OK, result);
        assertTSAOCSPEvents(null, "http://demo.sk.ee/ocsp");
    }

    @Test
    public void shouldRequest_TSA_AIAOCSP_WithSignatureProfile_LT_AndPreferAiaOcspTrue() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(true);
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);
        Result result = signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        sigaEventLogger.logEvents();
        assertEquals(Result.OK, result);
        assertTSAOCSPEvents("http://demo.sk.ee/tsa", "http://aia.demo.sk.ee/esteid2018");
    }

    private void assertTSAOCSPEvents(String tsaUrl, String ocspUrl) {
        SigaEvent finalizeSignatureEvent = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).get();
        SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();
        Optional<SigaEvent> tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH);

        assertNotNull(finalizeSignatureEvent);
        assertNotNull(ocspRequestEvent);
        assertEquals(ocspUrl, ocspRequestEvent.getEventParameter(REQUEST_URL));
        assertEquals(SUCCESS, finalizeSignatureEvent.getResultType());
        assertEquals(SUCCESS, ocspRequestEvent.getResultType());
        if (tsaUrl != null) {
            SigaEvent tsaEvent = tsaRequestEvent.get();
            assertNotNull(tsaEvent);
            assertEquals(tsaUrl, tsaEvent.getEventParameter(REQUEST_URL));
            assertEquals(SUCCESS, tsaEvent.getResultType());
        } else {
            assertFalse(tsaRequestEvent.isPresent());
        }
    }

    @Test(expected = DSSException.class)
    public void shouldNotRequest_TSA_OCSP_WithExpiredCertificate() throws IOException, URISyntaxException {
        try {
            Pair<String, String> signature = createSignature(EXPIRED_PKCS12_Esteid2011, SignatureProfile.LT);
            signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        } catch (DSSException e) {
            assertThat(e.getMessage(), containsString("is not in certificate validity range"));
            assertNull(sigaEventLogger.getEvent(0));
            throw e;
        }
    }

    @Test(expected = SignatureCreationException.class)
    public void shouldNotRequest_OCSP_AfterUnsuccessfulTSARequest() throws IOException, URISyntaxException {
        when(configuration.getTspSource()).thenReturn("http://demo.invalid.url.sk.ee/tsa");
        try {
            Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);
            signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        } catch (SignatureCreationException e) {
            assertThat(e.getMessage(), containsString("Unable to finalize signature"));
            sigaEventLogger.logEvents();
            SigaEvent finalizeSignatureEvent = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).get();
            SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();

            assertNotNull(finalizeSignatureEvent);
            assertNotNull(tsaRequestEvent);
            assertFalse(sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).isPresent());

            assertEquals("http://demo.invalid.url.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
            assertEquals(EXCEPTION, finalizeSignatureEvent.getResultType());
            assertEquals(EXCEPTION, tsaRequestEvent.getResultType());
            assertEquals(SIGNATURE_FINALIZING_REQUEST_ERROR.name(), finalizeSignatureEvent.getErrorCode());
            assertEquals(SIGNATURE_FINALIZING_REQUEST_ERROR.name(), tsaRequestEvent.getErrorCode());
            assertEquals("Failed to connect to TSP service <http://demo.invalid.url.sk.ee/tsa>. Service is down or URL is invalid.", finalizeSignatureEvent.getErrorMessage());
            assertEquals("Failed to connect to TSP service <http://demo.invalid.url.sk.ee/tsa>. Service is down or URL is invalid.", tsaRequestEvent.getErrorMessage());
            throw e;
        }
    }

    @Test(expected = SignatureCreationException.class)
    public void shouldRequest_TSA_BeforeUnsuccessfulOCSPRequest() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        when(configuration.getOcspSource()).thenReturn("http://aia.invalid.url.sk.ee/esteid2018");
        try {
            Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);
            Result result = signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        } catch (SignatureCreationException e) {
            assertThat(e.getMessage(), containsString("Unable to finalize signature"));
            sigaEventLogger.logEvents();
            SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).get();
            SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();
            SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();

            assertNotNull(ocspEvent);
            assertNotNull(tsaRequestEvent);
            assertNotNull(ocspRequestEvent);
            assertEquals(SUCCESS, tsaRequestEvent.getResultType());
            assertNull(tsaRequestEvent.getErrorCode());
            assertNull(tsaRequestEvent.getErrorMessage());
            assertEquals("http://demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
            assertEquals(EXCEPTION, ocspRequestEvent.getResultType());
            assertEquals(SIGNATURE_FINALIZING_REQUEST_ERROR.name(), ocspRequestEvent.getErrorCode());
            assertEquals("Failed to connect to OCSP service <http://aia.invalid.url.sk.ee/esteid2018>. Service is down or URL is invalid.", ocspRequestEvent.getErrorMessage());
            throw e;
        }

    }

    /**
     * No certificate to test with
     */
    @Ignore
    @Test(expected = TechnicalException.class)
    public void shouldRequest_TSA_OCSP_WithRevokedCertificate() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(true);
        try {
            Pair<String, String> signature = createSignature(REVOKED_PKCS12_Esteid2018, SignatureProfile.LT);
            signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        } catch (TechnicalException e) {
            assertEquals("Unable to finalize signature", e.getMessage());
            sigaEventLogger.logEvents();
            SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).get();
            SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();
            SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();

            assertNotNull(ocspEvent);
            assertNotNull(ocspRequestEvent);
            assertNotNull(tsaRequestEvent);
            assertEquals("http://demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
            assertEquals("http://aia.demo.sk.ee/esteid2018", ocspRequestEvent.getEventParameter(REQUEST_URL));
            assertEquals(EXCEPTION, ocspEvent.getResultType());
            assertEquals(EXCEPTION, tsaRequestEvent.getResultType());
            assertEquals(EXCEPTION, ocspRequestEvent.getResultType());
            assertEquals(SIGNATURE_FINALIZING_ERROR.name(), ocspEvent.getErrorCode());
            assertEquals(SIGNATURE_FINALIZING_ERROR.name(), tsaRequestEvent.getErrorCode());
            assertEquals(SIGNATURE_FINALIZING_ERROR.name(), ocspRequestEvent.getErrorCode());
            assertEquals("Revoked certificate detected", ocspEvent.getErrorMessage());
            assertEquals("Revoked certificate detected", tsaRequestEvent.getErrorMessage());
            assertEquals("Revoked certificate detected", ocspRequestEvent.getErrorMessage());

            throw e;
        }
    }

    /**
     * Corner case where issuing certificate is not trusted.
     *
     * @see <a href="https://jira.ria.ee/browse/DD4J-416">Jira task DD4J-416</a>
     */

    @Test
    public void shouldRequestOnly_TSA_WithUnknownIssuer() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(true);
        Pair<String, String> signature = createSignature(UNKNOWN_PKCS12_Esteid2018, SignatureProfile.LT);
        try {
            signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
            fail("Should not reach here!");
        } catch (SignatureCreationException e) {
            assertEquals("Unable to finalize signature. OCSP request failed. Issuing certificate may not be trusted.", e.getMessage());
        }
        sigaEventLogger.logEvents();
        assertTrue(sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).isPresent());
        Optional<SigaEvent> event = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH);
        assertTrue(event.isPresent());
        SigaEvent tsaRequestEvent = event.get();
        assertNotNull(tsaRequestEvent);
        assertEquals(SUCCESS, tsaRequestEvent.getResultType());
        assertNull(tsaRequestEvent.getErrorCode());
        assertNull(tsaRequestEvent.getErrorMessage());
        assertFalse(sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).isPresent());
        assertEquals("http://demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
        event = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH);
        assertTrue(event.isPresent());
        SigaEvent finalizeSignatureEvent = event.get();
        assertEquals(EXCEPTION, finalizeSignatureEvent.getResultType());
    }

    private Pair<String, String> createSignature(PKCS12SignatureToken signatureToken, SignatureProfile signatureProfile) throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = RequestUtil.createSignatureParameters(signatureToken.getCertificate(), signatureProfile);
        DataToSignWrapper dataToSignWrapper = signingService.createDataToSign(CONTAINER_ID, signatureParameters);
        DataToSign dataToSign = dataToSignWrapper.getDataToSign();
        byte[] signatureRaw = signatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());

        HashcodeContainerSessionHolder sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.addDataToSign(dataToSign.getSignatureParameters().getSignatureId(), DataToSignHolder.builder()
                .dataToSign(dataToSign)
                .signingType(SigningType.REMOTE)
                .dataFilesHash(signingService.generateDataFilesHash(sessionHolder))
                .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        return Pair.of(dataToSign.getSignatureParameters().getSignatureId(), new String(Base64.getEncoder().encode(signatureRaw)));
    }
}
