package ee.openeid.siga.common.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.codec.DecodingException;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class HttpClientImplDecodingExceptionAndSocketTimeoutTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private HttpClientImpl httpClient;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        httpClient = new HttpClientImpl(webClient);
    }

    @Test
    void post_WhenExchangeFunctionThrowsDecodingException_HttpClientDecodingExceptionIsThrown() {
        Object requestBody = new Object();
        DecodingException decodingException = new DecodingException("Invalid JSON format");

        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(decodingException));

        HttpClientDecodingException ex = assertThrows(HttpClientDecodingException.class,
                () -> httpClient.post("/path", requestBody, String.class));

        assertEquals("Error decoding response using Spring codecs", ex.getMessage());
        assertSame(decodingException, ex.getCause());
    }

    @Test
    void get_WhenExchangeFunctionThrowsSocketTimeout_HttpClientTimeoutExceptionIsThrown() {
        SocketTimeoutException timeoutException = new SocketTimeoutException("Read timed out");

        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(timeoutException));

        HttpClientTimeoutException ex = assertThrows(HttpClientTimeoutException.class,
                () -> httpClient.get("/path", String.class));

        assertEquals("Socket timeout occurred while waiting for response", ex.getMessage());
        assertSame(timeoutException, ex.getCause());
    }


    @Test
    void post_WhenExchangeFunctionThrowsSocketTimeout_HttpClientTimeoutExceptionIsThrown() {
        SocketTimeoutException timeoutException = new SocketTimeoutException("Read timed out");

        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.error(timeoutException));

        HttpClientTimeoutException ex = assertThrows(HttpClientTimeoutException.class, () ->
                httpClient.post("/path", new Object(), String.class));

        assertEquals("Socket timeout occurred while waiting for response", ex.getMessage());
        assertSame(timeoutException, ex.getCause());
    }

    @Test
    void get_WhenExchangeFunctionThrowsDecodingException_HttpClientDecodingExceptionIsThrown() {
        DecodingException decodingException = new DecodingException("Invalid JSON format");

        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(decodingException));

        HttpClientDecodingException ex = assertThrows(HttpClientDecodingException.class,
                () -> httpClient.get("/path", String.class));

        assertEquals("Error decoding response using Spring codecs", ex.getMessage());
        assertSame(decodingException, ex.getCause());
    }

}







