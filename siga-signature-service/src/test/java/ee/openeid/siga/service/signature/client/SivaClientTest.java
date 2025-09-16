package ee.openeid.siga.service.signature.client;

import ee.openeid.siga.common.client.HttpPostClient;
import ee.openeid.siga.common.client.HttpStatusException;
import ee.openeid.siga.common.client.HttpClientTlsHandshakeException;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.SiVaHttpErrorException;
import ee.openeid.siga.common.exception.SiVaTlsHandshakeException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class SivaClientTest {

    @InjectMocks
    private SivaClient sivaClient;
    @Mock
    private HttpPostClient httpClient;

    @Test
    void successfulSivaResponse() throws Exception {
        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenReturn(RequestUtil.createValidationResponse());

        ValidationConclusion response = sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(),
                RequestUtil.createHashcodeDataFileListWithOneFile());

        assertEquals(Integer.valueOf(1), response.getSignaturesCount());
        assertEquals(Integer.valueOf(1), response.getValidSignaturesCount());
    }

    @Test
    void invalidPlusSignEncodingInSignaturesFileShouldStillPass() throws Exception {
        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenReturn(RequestUtil.createValidationResponse());
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(TestUtil.getFile("hashcodePlusCharacterInFilename.asice"));
        List<HashcodeSignatureWrapper> signatureWrappers = hashcodeContainer.getSignatures();
        // Plus sign in XML parameters is decoded to space character
        signatureWrappers.get(0).getDataFiles().get(0).setFileName("This is a test");

        ValidationConclusion response = sivaClient.validateHashcodeContainer(signatureWrappers,
                RequestUtil.createHashcodeDataFileListWithOneFile("This+is+a+test"));

        assertEquals(Integer.valueOf(1), response.getSignaturesCount());
        assertEquals(Integer.valueOf(1), response.getValidSignaturesCount());
    }

    @ParameterizedTest
    @MethodSource("httpStatusProvider")
    void validateHashcodeContainer_WhenHttpClientThrowsHttpStatusException_SivaHttpErrorExceptionIsThrown(HttpStatus status) {
        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)
        )).thenThrow(new HttpStatusException(status, ArrayUtils.EMPTY_BYTE_ARRAY));

        SiVaHttpErrorException caughtException = assertThrows(
                SiVaHttpErrorException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(),
                        RequestUtil.createHashcodeDataFileListWithOneFile()));

        assertEquals("Unable to get a valid response from SiVa", caughtException.getMessage());
    }

    @Test
    void validateHashcodeContainer_WhenHttpClientThrowsGenericException_TechnicalExceptionIsThrown() {
        Exception genericException = new RuntimeException("Some unexpected error");

        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)
        )).thenThrow(genericException);

        TechnicalException caughtException = assertThrows(
            TechnicalException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(),
                        RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("SIVA service error", caughtException.getMessage());
        assertEquals(genericException, caughtException.getCause());
    }

    @Test
    void validateHashcodeContainer_WhenHttpClientTlsHandshakeException_SiVaTlsHandshakeExceptionIsThrown() {
        HttpClientTlsHandshakeException expectedException = new HttpClientTlsHandshakeException("Something happened", new Exception());
        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)
        )).thenThrow(expectedException);

        SiVaTlsHandshakeException caughtException = assertThrows(
                SiVaTlsHandshakeException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(),
                        RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("TLS handshake with SiVa service failed", caughtException.getMessage());
        assertEquals(expectedException, caughtException.getCause());
    }

    @Test
    void hashMismatch() throws IOException, URISyntaxException {
        List<HashcodeSignatureWrapper> signatureWrappers = RequestUtil.createSignatureWrapper();
        signatureWrappers.get(0).getDataFiles().get(0).setHashAlgo("SHA386");

        InvalidHashAlgorithmException caughtException = assertThrows(
            InvalidHashAlgorithmException.class, () -> sivaClient.validateHashcodeContainer(signatureWrappers, RequestUtil.createHashcodeDataFiles())
        );
        assertEquals("Container contains invalid hash algorithms", caughtException.getMessage());
    }

    @Test
    void sivaDocumentMalformed() {
        String body = "{\"requestErrors\": [{\n" +
                "    \"message\": \"Document malformed or not matching documentType\",\n" +
                "    \"key\": \"document\"\n" +
                "}]}";
        byte[] exceptionBytes = body.getBytes(StandardCharsets.UTF_8);

        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenThrow(new HttpStatusException(HttpStatus.BAD_REQUEST, exceptionBytes));

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(),
                        RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Document malformed", caughtException.getMessage());
    }

    @Test
    void sivaSignatureMalformed() {
        String body = "{\"requestErrors\": [{\n" +
                "    \"message\": \" Signature file malformed\",\n" +
                "    \"key\": \"signatureFiles.signature\"\n" +
                "}]}";
        byte[] exceptionBytes = body.getBytes(StandardCharsets.UTF_8);

        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenThrow(new HttpStatusException(HttpStatus.BAD_REQUEST, exceptionBytes));

        InvalidSignatureException caughtException = assertThrows(
            InvalidSignatureException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(),
                        RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Signature malformed", caughtException.getMessage());
    }

    static Stream<HttpStatus> httpStatusProvider() {
        return Stream.concat(
                Stream.of((HttpStatus) null),
                Stream.of(HttpStatus.values())
                        .filter(Predicate.not(HttpStatus.BAD_REQUEST::equals)));
    }
}
