package ee.openeid.siga.service.signature.client;

import ee.openeid.siga.common.client.HttpClientTlsHandshakeException;
import ee.openeid.siga.common.client.HttpPostClient;
import ee.openeid.siga.common.client.HttpStatusException;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.SiVaHttpErrorException;
import ee.openeid.siga.common.exception.SiVaTlsHandshakeException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.test.CommonTestUtil;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.webapp.json.SignatureValidationData;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
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

import static ee.openeid.siga.service.signature.test.matcher.IsSignatureFile.isSignatureFileWith;
import static ee.openeid.siga.service.signature.test.matcher.IsSivaDataFile.isSiVaDataFileWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SivaClientTest {

    private static final String MOCK_CONTAINER_NAME = "test.asice";
    private static final String MOCK_CONTAINER_BASE64 = "QVNpQy1FIGtvbnRlaW5lcg==";

    @InjectMocks
    private SivaClient sivaClient;
    @Mock
    private HttpPostClient httpClient;

    @ParameterizedTest
    @MethodSource("validationEndpointCalls")
    void successfulSivaResponse(String endpointPath, ValidationCall validationCall) throws Exception {
        ValidationResponse validationResponse = RequestUtil.createValidationResponse();
        ValidationConclusion expectedConclusion = validationResponse.getValidationReport().getValidationConclusion();
        when(httpClient.post(Mockito.eq(endpointPath), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenReturn(validationResponse);

        ValidationConclusion response = validationCall.callValidateOn(sivaClient);

        assertThat(response, sameInstance(expectedConclusion));
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

    @Test
    void validateContainer_PassesExpectedSivaValidationRequestToHttpClient() {
        when(httpClient.post(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(RequestUtil.createValidationResponse());

        sivaClient.validateContainer(MOCK_CONTAINER_NAME, MOCK_CONTAINER_BASE64);

        ArgumentCaptor<SivaValidationRequest> requestCaptor = ArgumentCaptor.forClass(SivaValidationRequest.class);
        verify(httpClient).post(Mockito.eq("/validate"), requestCaptor.capture(), Mockito.eq(ValidationResponse.class));
        verifyNoMoreInteractions(httpClient);
        SivaValidationRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getDocument(), equalTo(MOCK_CONTAINER_BASE64));
        assertThat(capturedRequest.getFilename(), equalTo(MOCK_CONTAINER_NAME));
    }

    @Test
    void validateHashcodeContainer_PassesExpectedSivaHashcodeValidationRequestToHttpClient() throws Exception {
        List<HashcodeSignatureWrapper> signatureWrappers = RequestUtil.createSignatureWrapper();
        List<HashcodeDataFile> dataFiles = RequestUtil.createHashcodeDataFileListWithOneFile();
        when(httpClient.post(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(RequestUtil.createValidationResponse());

        sivaClient.validateHashcodeContainer(signatureWrappers, dataFiles);

        ArgumentCaptor<SivaHashcodeValidationRequest> requestCaptor = ArgumentCaptor.forClass(SivaHashcodeValidationRequest.class);
        verify(httpClient).post(Mockito.eq("/validateHashcode"), requestCaptor.capture(), Mockito.eq(ValidationResponse.class));
        verifyNoMoreInteractions(httpClient);
        SivaHashcodeValidationRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getSignatureFiles(), contains(
                isSignatureFileWith(
                        signatureWrappers.get(0).getSignature(),
                        contains(
                                isSiVaDataFileWith(
                                        signatureWrappers.get(0).getDataFiles().get(0).getFileName(),
                                        signatureWrappers.get(0).getDataFiles().get(0).getHashAlgo(),
                                        dataFiles.get(0).getFileHashSha256()
                                )
                        )
                )
        ));
        assertThat(capturedRequest.getValidationLevel(), equalTo("LongTermData"));
    }

    @ParameterizedTest
    @EnumSource(SignatureLevel.class)
    void validateHashcodeContainer_SucceedsWithAnySignatureFormat(SignatureLevel signatureLevel) throws Exception {
        ValidationResponse validationResponse = RequestUtil.createValidationResponse();
        ValidationConclusion validationConclusion = validationResponse.getValidationReport().getValidationConclusion();
        SignatureValidationData signatureValidationData = new SignatureValidationData();
        signatureValidationData.setSignatureFormat(signatureLevel.name());
        validationConclusion.getSignatures().add(signatureValidationData);
        when(httpClient.post(Mockito.eq("/validateHashcode"), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenReturn(validationResponse);

        ValidationConclusion response = sivaClient.validateHashcodeContainer(
                RequestUtil.createSignatureWrapper(),
                RequestUtil.createHashcodeDataFileListWithOneFile()
        );

        assertThat(response, sameInstance(validationConclusion));
    }

    @ParameterizedTest
    @MethodSource("validationEndpointCallsWithHttpStatus")
    void validate_WhenHttpClientThrowsHttpStatusException_SivaHttpErrorExceptionIsThrown(
            String endpointPath,
            ValidationCall validationCall,
            HttpStatus status
    ) {
        when(httpClient.post(Mockito.eq(endpointPath), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenThrow(new HttpStatusException(status, ArrayUtils.EMPTY_BYTE_ARRAY));

        SiVaHttpErrorException caughtException = assertThrows(
                SiVaHttpErrorException.class,
                () -> validationCall.callValidateOn(sivaClient)
        );

        assertEquals("Unable to get a valid response from SiVa", caughtException.getMessage());
    }

    static Stream<Arguments> validationEndpointCallsWithHttpStatus() {
        return validationEndpointCalls().flatMap(call -> httpStatusProvider()
                .map(status -> CommonTestUtil.concatArguments(call, status)));
    }

    @ParameterizedTest
    @MethodSource("validationEndpointCalls")
    void validate_WhenHttpClientThrowsGenericException_TechnicalExceptionIsThrown(
            String endpointPath,
            ValidationCall validationCall
    ) {
        Exception genericException = new RuntimeException("Some unexpected error");

        when(httpClient.post(Mockito.eq(endpointPath), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenThrow(genericException);

        TechnicalException caughtException = assertThrows(
                TechnicalException.class,
                () -> validationCall.callValidateOn(sivaClient)
        );
        assertEquals("SIVA service error", caughtException.getMessage());
        assertEquals(genericException, caughtException.getCause());
    }

    @ParameterizedTest
    @MethodSource("validationEndpointCalls")
    void validate_WhenHttpClientTlsHandshakeException_SiVaTlsHandshakeExceptionIsThrown(
            String endpointPath,
            ValidationCall validationCall
    ) {
        HttpClientTlsHandshakeException expectedException = new HttpClientTlsHandshakeException("Something happened", new Exception());
        when(httpClient.post(Mockito.eq(endpointPath), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenThrow(expectedException);

        SiVaTlsHandshakeException caughtException = assertThrows(
                SiVaTlsHandshakeException.class,
                () -> validationCall.callValidateOn(sivaClient)
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

    @ParameterizedTest
    @MethodSource("validationEndpointCalls")
    void sivaDocumentMalformed(String endpointPath, ValidationCall validationCall) {
        String body = """
                {"requestErrors": [{
                    "message": "Document malformed or not matching documentType",
                    "key": "document"
                }]}""";
        byte[] exceptionBytes = body.getBytes(StandardCharsets.UTF_8);

        when(httpClient.post(Mockito.eq(endpointPath), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenThrow(new HttpStatusException(HttpStatus.BAD_REQUEST, exceptionBytes));

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class,
                () -> validationCall.callValidateOn(sivaClient)
        );
        assertEquals("Document malformed", caughtException.getMessage());
    }

    @ParameterizedTest
    @MethodSource("validationEndpointCalls")
    void sivaSignatureMalformed(String endpointPath, ValidationCall validationCall) {
        String body = """
                {"requestErrors": [{
                    "message": " Signature file malformed",
                    "key": "signatureFiles.signature"
                }]}""";
        byte[] exceptionBytes = body.getBytes(StandardCharsets.UTF_8);

        when(httpClient.post(Mockito.eq(endpointPath), Mockito.any(), Mockito.eq(ValidationResponse.class)))
                .thenThrow(new HttpStatusException(HttpStatus.BAD_REQUEST, exceptionBytes));

        InvalidSignatureException caughtException = assertThrows(
                InvalidSignatureException.class,
                () -> validationCall.callValidateOn(sivaClient)
        );
        assertEquals("Signature malformed", caughtException.getMessage());
    }

    static Stream<Arguments> validationEndpointCalls() {
        return Stream.of(
                Arguments.of(
                        "/validate",
                        (ValidationCall) client -> client
                                .validateContainer(MOCK_CONTAINER_NAME, MOCK_CONTAINER_BASE64)
                ),
                Arguments.of(
                        "/validateHashcode",
                        (ValidationCall) client -> client.validateHashcodeContainer(
                                RequestUtil.createSignatureWrapper(),
                                RequestUtil.createHashcodeDataFileListWithOneFile()
                        )
                )
        );
    }

    static Stream<HttpStatus> httpStatusProvider() {
        return Stream.concat(
                Stream.of((HttpStatus) null),
                Stream.of(HttpStatus.values())
                        .filter(Predicate.not(HttpStatus.BAD_REQUEST::equals)));
    }

    @FunctionalInterface
    interface ValidationCall {
        ValidationConclusion callValidateOn(SivaClient sivaClient) throws Exception;
    }

}
