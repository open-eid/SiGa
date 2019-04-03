package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.DSSException;
import org.digidoc4j.*;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Optional;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEvent.EventResultType.SUCCESS;
import static ee.openeid.siga.common.event.SigaEvent.EventType.FINISH;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.OCSP_URL;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.TSA_URL;
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
    private DetachedDataFileContainerSigningService signingService;

    @Mock
    private MobileIdService mobileIdService;

    @Mock
    private DigiDocService digiDocService;

    @Mock
    private SessionService sessionService;

    @Spy
    private Configuration configuration = new Configuration(Configuration.Mode.TEST);

    @Spy
    private SigaEventLogger sigaEventLogger;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        configuration.setPreferAiaOcsp(true);
        signingService.setConfiguration(configuration);
        when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createSessionHolder());
        sigaEventLogger.afterPropertiesSet();
    }

    @Test
    public void shouldRequest_TSA_OCSP_WithSignatureProfile_LT_AndPreferAiaOcspFalse() {
        configuration.setPreferAiaOcsp(false);
        String result = signingService.finalizeSigning(CONTAINER_ID, createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT));
        sigaEventLogger.logEvents();
        assertEquals("OK", result);
        assertTSAOCSPEvents("http://demo.sk.ee/tsa", "http://demo.sk.ee/ocsp");
    }

    @Test
    public void shouldRequest_TSA_OCSP_WithSignatureProfile_LT_TM_AndPreferAiaOcspFalse() {
        configuration.setPreferAiaOcsp(false);
        String result = signingService.finalizeSigning(CONTAINER_ID, createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT_TM));
        sigaEventLogger.logEvents();
        assertEquals("OK", result);
        assertTSAOCSPEvents("http://demo.sk.ee/tsa", "http://demo.sk.ee/ocsp");
    }

    @Ignore
    @Test
    public void shouldRequestOnly_OCSP_WithSignatureProfile_LT_TM_AndPreferAiaOcspTrue() {
        configuration.setPreferAiaOcsp(true);
        String result = signingService.finalizeSigning(CONTAINER_ID, createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT_TM));
        sigaEventLogger.logEvents();
        assertEquals("OK", result);
        assertTSAOCSPEvents(null, "http://demo.sk.ee/ocsp");
    }

    @Test
    public void shouldRequest_TSA_AIAOCSP_WithSignatureProfile_LT_AndPreferAiaOcspTrue() {
        configuration.setPreferAiaOcsp(true);
        String result = signingService.finalizeSigning(CONTAINER_ID, createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT));
        sigaEventLogger.logEvents();
        assertEquals("OK", result);
        assertTSAOCSPEvents("http://demo.sk.ee/tsa", "http://aia.demo.sk.ee/esteid2018");
    }

    private void assertTSAOCSPEvents(String tsaUrl, String ocspUrl) {
        SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(OCSP, FINISH).get();
        SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();
        Optional<SigaEvent> tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH);

        assertNotNull(ocspEvent);
        assertNotNull(ocspRequestEvent);
        assertEquals(ocspUrl, ocspRequestEvent.getEventParameter(OCSP_URL));
        assertEquals(SUCCESS, ocspEvent.getResultType());
        assertEquals(SUCCESS, ocspRequestEvent.getResultType());
        if (tsaUrl != null) {
            SigaEvent tsaEvent = tsaRequestEvent.get();
            assertNotNull(tsaEvent);
            assertEquals(tsaUrl, tsaEvent.getEventParameter(TSA_URL));
            assertEquals(SUCCESS, tsaEvent.getResultType());
        } else {
            assertFalse(tsaRequestEvent.isPresent());
        }
    }

    @Test(expected = DSSException.class)
    public void shouldNotRequest_TSA_OCSP_WithExpiredCertificate() {
        try {
            signingService.finalizeSigning(CONTAINER_ID, createSignature(EXPIRED_PKCS12_Esteid2011, SignatureProfile.LT));
        } catch (DSSException e) {
            assertThat(e.getMessage(), containsString("is not in certificate validity range"));
            assertNull(sigaEventLogger.getEvent(0));
            throw e;
        }
    }

    @Test(expected = TechnicalException.class)
    public void shouldNotRequest_OCSP_AfterUnsuccessfulTSARequest() {
        when(configuration.getTspSource()).thenReturn("http://demo.invalid.url.sk.ee/tsa");
        try {
            signingService.finalizeSigning(CONTAINER_ID, createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT));
        } catch (TechnicalException e) {
            assertThat(e.getMessage(), containsString("Unable to finalize signature"));
            sigaEventLogger.logEvents();
            SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(OCSP, FINISH).get();
            SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();

            assertNotNull(ocspEvent);
            assertNotNull(tsaRequestEvent);
            assertFalse(sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).isPresent());
            assertEquals("http://demo.invalid.url.sk.ee/tsa", tsaRequestEvent.getEventParameter(TSA_URL));
            assertEquals(EXCEPTION, ocspEvent.getResultType());
            assertEquals(EXCEPTION, tsaRequestEvent.getResultType());
            assertEquals(SIGNATURE_FINALIZING_REQUEST_ERROR.name(), ocspEvent.getErrorCode());
            assertEquals(SIGNATURE_FINALIZING_REQUEST_ERROR.name(), tsaRequestEvent.getErrorCode());
            assertEquals("Unable to process GET call for url 'http://demo.invalid.url.sk.ee/tsa'", ocspEvent.getErrorMessage());
            assertEquals("Unable to process GET call for url 'http://demo.invalid.url.sk.ee/tsa'", tsaRequestEvent.getErrorMessage());
            throw e;
        }
    }

    @Test
    public void shouldRequest_TSA_BeforeUnsuccessfulOCSPRequest() {
        configuration.setPreferAiaOcsp(false);
        when(configuration.getOcspSource()).thenReturn("http://aia.invalid.url.sk.ee/esteid2018");
        String result = signingService.finalizeSigning(CONTAINER_ID, createSignature(VALID_PKCS12_Esteid2018, SignatureProfile.LT));
        sigaEventLogger.logEvents();
        SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(OCSP, FINISH).get();
        SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();
        SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();

        assertNotNull(ocspEvent);
        assertNotNull(tsaRequestEvent);
        assertNotNull(ocspRequestEvent);
        assertEquals(SUCCESS, tsaRequestEvent.getResultType());
        assertNull(tsaRequestEvent.getErrorCode());
        assertNull(tsaRequestEvent.getErrorMessage());
        assertEquals("http://demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(TSA_URL));
        assertEquals(EXCEPTION, ocspRequestEvent.getResultType());
        assertEquals(SIGNATURE_FINALIZING_REQUEST_ERROR.name(), ocspRequestEvent.getErrorCode());
        assertEquals("OCSP DSS Exception: Unable to process GET call for url 'http://aia.invalid.url.sk.ee/esteid2018'", ocspRequestEvent.getErrorMessage());
        assertEquals(SUCCESS, ocspEvent.getResultType());
        assertEquals("OK", result);
    }

    /**
     * No certificate to test with
     */
    @Ignore
    @Test(expected = TechnicalException.class)
    public void shouldRequest_TSA_OCSP_WithRevokedCertificate() {
        configuration.setPreferAiaOcsp(true);
        try {
            signingService.finalizeSigning(CONTAINER_ID, createSignature(REVOKED_PKCS12_Esteid2018, SignatureProfile.LT));
        } catch (TechnicalException e) {
            assertEquals("Unable to finalize signature", e.getMessage());
            sigaEventLogger.logEvents();
            SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(OCSP, FINISH).get();
            SigaEvent ocspRequestEvent = sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).get();
            SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();

            assertNotNull(ocspEvent);
            assertNotNull(ocspRequestEvent);
            assertNotNull(tsaRequestEvent);
            assertEquals("http://demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(TSA_URL));
            assertEquals("http://aia.demo.sk.ee/esteid2018", ocspRequestEvent.getEventParameter(OCSP_URL));
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
    @Ignore
    @Test
    public void shouldRequestOnly_TSA_WithUnknownIssuer() {
        configuration.setPreferAiaOcsp(true);
        String result = signingService.finalizeSigning(CONTAINER_ID, createSignature(UNKNOWN_PKCS12_Esteid2018, SignatureProfile.LT));
        sigaEventLogger.logEvents();
        SigaEvent ocspEvent = sigaEventLogger.getFirstMachingEvent(OCSP, FINISH).get();
        SigaEvent tsaRequestEvent = sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).get();
        assertNotNull(ocspEvent);
        assertNotNull(tsaRequestEvent);
        assertFalse(sigaEventLogger.getFirstMachingEvent(OCSP_REQUEST, FINISH).isPresent());
        assertEquals("http://demo.sk.ee/tsa", tsaRequestEvent.getEventParameter(TSA_URL));
        assertEquals("OK", result);
    }

    private String createSignature(PKCS12SignatureToken signatureToken, SignatureProfile signatureProfile) {
        SignatureParameters signatureParameters = RequestUtil.createSignatureParameters(signatureToken.getCertificate(), signatureProfile);
        DataToSign dataToSign = signingService.createDataToSign(CONTAINER_ID, signatureParameters);
        byte[] signatureRaw = signatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        return new String(Base64.getEncoder().encode(signatureRaw));
    }
}
