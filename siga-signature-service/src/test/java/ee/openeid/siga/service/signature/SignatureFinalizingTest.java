package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.SignatureCreationException;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import org.apache.commons.lang3.tuple.Pair;
import org.digidoc4j.*;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureFinalizingTest {
    private final PKCS12SignatureToken VALID_PKCS12_Esteid2018 = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());
    private final PKCS12SignatureToken REVOKED_STATE_PKCS12_Esteid2018 = new PKCS12SignatureToken("src/test/resources/p12/sign_revoked_state_ESTEID2018.p12", "1234".toCharArray());
    private final PKCS12SignatureToken UNKNOWN_STATE_PKCS12_Esteid2018 = new PKCS12SignatureToken("src/test/resources/p12/sign_unknown_state_ESTEID2018.p12", "1234".toCharArray());
    private final PKCS12SignatureToken UNKNOWN_ISSUER_PKCS12_Esteid2018 = new PKCS12SignatureToken("src/test/resources/p12/sign_unknown_issuer_ESTEID2018.p12", "1234".toCharArray());
    private final PKCS12SignatureToken EXPIRED_PKCS12_Esteid2011 = new PKCS12SignatureToken("src/test/resources/p12/expired_signer_ESTEID-SK 2011.p12", "test".toCharArray());
    private final PKCS12SignatureToken VALID_PKCS12_EsteidSK2015 = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEIDSK2015.p12", "1234".toCharArray());

    @InjectMocks
    private HashcodeContainerSigningService signingService;

    @Mock
    private SessionService sessionService;

    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);;

    private final SigaEventLogger sigaEventLogger = new SigaEventLogger();

    @Mock
    private Authentication authentication;
    @Mock
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder().build());
        SecurityContextHolder.setContext(securityContext);
        configuration.setPreferAiaOcsp(true);
        when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createHashcodeSessionHolder());
        signingService.setSessionService(sessionService);
        signingService.setSigaEventLogger(sigaEventLogger);
    }

    @AfterEach
    void clearLogs() {
        sigaEventLogger.logEvents();
    }

    @Test
    void shouldRequest_TSA_OCSP_WithSignatureProfile_LT_AndPreferAiaOcspFalse() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);
        Result result = signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        assertEquals(Result.OK, result);
        assertTSAOCSPEvents("http://tsa.demo.sk.ee/tsa", "http://demo.sk.ee/ocsp");
    }

    @Test
    void shouldRequest_TSA_AIAOCSP_WithSignatureProfile_LT_AndPreferAiaOcspTrue() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(true);
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);
        Result result = signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        assertEquals(Result.OK, result);
        assertTSAOCSPEvents("http://tsa.demo.sk.ee/tsa", "http://aia.demo.sk.ee/esteid2018");
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

    @Test
    void shouldNotRequest_TSA_OCSP_WithExpiredCertificate() {
        Exception e = assertThrows(IllegalArgumentException.class, () -> {
            Pair<String, String> signature = createSignature(EXPIRED_PKCS12_Esteid2011, SignatureProfile.LT);
            signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        });

        assertThat(e.getMessage(), matchesPattern(String.format(
                "The signing certificate \\(notBefore : %s, notAfter : %s\\) is expired at signing time .*",
                Date.from(OffsetDateTime.of(
                        LocalDate.of(2011, Month.APRIL, 8),
                        LocalTime.of(16, 35, 52),
                        ZoneOffset.ofHours(3)).toInstant()),
                Date.from(OffsetDateTime.of(
                        LocalDate.of(2014, Month.APRIL, 7),
                        LocalTime.of(23, 59, 59),
                        ZoneOffset.ofHours(3)).toInstant())
        )));
        assertNull(sigaEventLogger.getEvent(0));
    }

    @Test
    void shouldNotRequest_OCSP_AfterUnsuccessfulTSARequest() throws IOException, URISyntaxException {
        when(configuration.getTspSource()).thenReturn("http://demo.invalid.url.sk.ee/tsa");
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);

        Exception e = assertThrows(SignatureCreationException.class, () -> signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight()));

        assertThat(e.getMessage(), containsString("Unable to finalize signature"));
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
    }

    @Test
    void shouldRequest_TSA_BeforeUnsuccessfulOCSPRequest() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        when(configuration.getOcspSource()).thenReturn("http://aia.invalid.url.sk.ee/esteid2018");
        Pair<String, String> signature = createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT);

        Exception e = assertThrows(SignatureCreationException.class, () -> signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight()));

        assertThat(e.getMessage(), containsString("Unable to finalize signature"));
        SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).get();
        SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();
        SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();
        assertNotNull(ocspEvent);
        assertNotNull(tsaRequestEvent);
        assertNotNull(ocspRequestEvent);
        assertEquals(SUCCESS, tsaRequestEvent.getResultType());
        assertNull(tsaRequestEvent.getErrorCode());
        assertNull(tsaRequestEvent.getErrorMessage());
        assertEquals("http://tsa.demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
        assertEquals(EXCEPTION, ocspRequestEvent.getResultType());
        assertEquals(SIGNATURE_FINALIZING_REQUEST_ERROR.name(), ocspRequestEvent.getErrorCode());
        assertEquals("Failed to connect to OCSP service <http://aia.invalid.url.sk.ee/esteid2018>. Service is down or URL is invalid.", ocspRequestEvent.getErrorMessage());
    }

    @Test
    void shouldRequest_TSA_and_OCSP_WithRevokedCertificate() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        Pair<String, String> signature = createSignature(REVOKED_STATE_PKCS12_Esteid2018, SignatureProfile.LT);

        SignatureCreationException e = assertThrows(SignatureCreationException.class, () -> signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight()));

        assertEquals("Unable to finalize signature. Certificate status is revoked", e.getMessage());
        SigaEvent finalizeSignatureEvent = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).get();
        SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();
        SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();
        assertNotNull(finalizeSignatureEvent);
        assertNotNull(ocspRequestEvent);
        assertNotNull(tsaRequestEvent);
        assertEquals("http://tsa.demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
        assertEquals("http://demo.sk.ee/ocsp", ocspRequestEvent.getEventParameter(REQUEST_URL));
        assertEquals(EXCEPTION, finalizeSignatureEvent.getResultType());
        assertEquals(SUCCESS, tsaRequestEvent.getResultType());
        assertEquals(SUCCESS, ocspRequestEvent.getResultType());
        assertEquals(SIGNATURE_FINALIZING_ERROR.name(), finalizeSignatureEvent.getErrorCode());
        assertNull(tsaRequestEvent.getErrorCode());
        assertNull(ocspRequestEvent.getErrorCode());
        assertEquals("Certificate status is revoked", finalizeSignatureEvent.getErrorMessage());
        assertNull(tsaRequestEvent.getErrorMessage());
        assertNull(ocspRequestEvent.getErrorMessage());
    }

    @Test
    void shouldRequest_TSA_and_OCSP_WithUnknownCertificate() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        Pair<String, String> signature = createSignature(UNKNOWN_STATE_PKCS12_Esteid2018, SignatureProfile.LT);

        SignatureCreationException e = assertThrows(SignatureCreationException.class, () -> signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight()));

        assertEquals("Unable to finalize signature. Certificate is unknown", e.getMessage());
        SigaEvent finalizeSignatureEvent = sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).get();
        SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();
        SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();
        assertNotNull(finalizeSignatureEvent);
        assertNotNull(ocspRequestEvent);
        assertNotNull(tsaRequestEvent);
        assertEquals("http://tsa.demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
        assertEquals("http://demo.sk.ee/ocsp", ocspRequestEvent.getEventParameter(REQUEST_URL));
        assertEquals(EXCEPTION, finalizeSignatureEvent.getResultType());
        assertEquals(SUCCESS, tsaRequestEvent.getResultType());
        assertEquals(EXCEPTION, ocspRequestEvent.getResultType());
        assertEquals(SIGNATURE_FINALIZING_ERROR.name(), finalizeSignatureEvent.getErrorCode());
        assertNull(tsaRequestEvent.getErrorCode());
        assertEquals(SIGNATURE_FINALIZING_ERROR.name(), ocspRequestEvent.getErrorCode());
        assertEquals("Certificate is unknown", finalizeSignatureEvent.getErrorMessage());
        assertNull(tsaRequestEvent.getErrorMessage());
        assertEquals("Certificate is unknown", ocspRequestEvent.getErrorMessage());
    }

    /**
     * Corner case where issuing certificate is not trusted.
     *
     * @see <a href="https://jira.ria.ee/browse/DD4J-416">Jira task DD4J-416</a>
     */
    @Test
    void shouldRequestOnly_TSA_WithUnknownIssuer() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(false);
        Pair<String, String> signature = createSignature(UNKNOWN_ISSUER_PKCS12_Esteid2018, SignatureProfile.LT);

        Exception e = assertThrows(SignatureCreationException.class, () -> {
            signingService.finalizeSigning(CONTAINER_ID, signature.getLeft(), signature.getRight());
        });

        assertEquals("Unable to finalize signature. OCSP request failed. Issuing certificate may not be trusted.", e.getMessage());
        assertTrue(sigaEventLogger.getFirstMachingEvent(FINALIZE_SIGNATURE, FINISH).isPresent());
        Optional<SigaEvent> event = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH);
        assertTrue(event.isPresent());
        SigaEvent tsaRequestEvent = event.get();
        assertNotNull(tsaRequestEvent);
        assertEquals(SUCCESS, tsaRequestEvent.getResultType());
        assertNull(tsaRequestEvent.getErrorCode());
        assertNull(tsaRequestEvent.getErrorMessage());
        assertFalse(sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).isPresent());
        assertEquals("http://tsa.demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(REQUEST_URL));
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

        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.addSignatureSession(dataToSign.getSignatureParameters().getSignatureId(), SignatureSession.builder()
                .dataToSign(dataToSign)
                .signingType(SigningType.REMOTE)
                .dataFilesHash(signingService.generateDataFilesHash(sessionHolder))
                .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        return Pair.of(dataToSign.getSignatureParameters().getSignatureId(), new String(Base64.getEncoder().encode(signatureRaw)));
    }
}
