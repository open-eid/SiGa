package ee.openeid.siga.common.client;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class HttpClientImpl implements HttpGetClient, HttpPostClient {
    @NonNull
    private final WebClient webClient;

    @Override
    public <T> T get(String path, Class<T> responseType) {
        return webClient.get()
                .uri(uriBuilder(path))
                .exchangeToMono(clientResponseHandler(responseType))
                .onErrorMap(HttpClientImpl::mapToCustomException)
                .block();
    }

    @Override
    public <T> T post(String path, Object requestBody, Class<T> responseType) {
        return webClient.post()
                .uri(uriBuilder(path))
                .bodyValue(requestBody)
                .exchangeToMono(clientResponseHandler(responseType))
                .onErrorMap(HttpClientImpl::mapToCustomException)
                .block();
    }

    private static Function<UriBuilder, URI> uriBuilder(String path) {
        return uriBuilder -> uriBuilder
                .path(path)
                .build();
    }

    private static <T> Function<ClientResponse, Mono<T>> clientResponseHandler(Class<T> responseType) {
        return clientResponse -> Mono.just(clientResponse)
                .flatMap(HttpClientImpl::verifyResponseStatus)
                .flatMap(response -> response.bodyToMono(responseType));
    }

    private static Mono<ClientResponse> verifyResponseStatus(ClientResponse clientResponse) {
        int statusCode = clientResponse.statusCode().value();
        if (statusCode == HttpStatus.OK.value()) {
            return Mono.just(clientResponse);
        }
        return clientResponse.bodyToMono(byte[].class)
                .defaultIfEmpty(ArrayUtils.EMPTY_BYTE_ARRAY)
                .map(bytes -> {
                    HttpStatus httpStatus = HttpStatus.resolve(statusCode);
                    throw new HttpStatusException(httpStatus, bytes);
                });
    }

    private static Throwable mapToCustomException(Throwable e) {
        Throwable cause;

        if ((cause = getCauseOfType(e,
                java.net.SocketTimeoutException.class,
                io.netty.handler.timeout.ReadTimeoutException.class)) != null) {
            return new HttpClientTimeoutException("Read timeout occurred", cause);
        }
        //Jenkins might throw this depending on URL
        if ((cause = getCauseOfType(e,
                io.netty.resolver.dns.DnsErrorCauseException.class,
                java.net.UnknownHostException.class,
                io.netty.resolver.dns.DnsNameResolverTimeoutException.class)) != null ) {
            return new HttpClientConnectionException("Service unreachable", cause);
        }
        if ((cause = getCauseOfType(e, java.net.ConnectException.class)) != null) {
            return new HttpClientConnectionException("Unable to connect to service", cause);
        }
        if ((cause = getCauseOfType(e,
                org.springframework.core.codec.DecodingException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class)) != null) {
            return new HttpClientDecodingException("Error processing JSON response", cause);
        }
        if ((cause = getCauseOfType(e, HttpStatusException.class)) != null) {
            return cause;
        }
        return e;
    }

    @SafeVarargs
    private static Throwable getCauseOfType(Throwable throwable, Class<? extends Throwable>... causeTypes) {
        while (throwable != null) {
            if (isOfType(throwable, causeTypes)) {
                return throwable;
            } else if (throwable.getCause() == throwable) {
                break;
            }
            throwable = throwable.getCause();
        }
        return null;
    }

    private static boolean isOfType(Object object, Class<?>... types) {
        return Stream.of(types).anyMatch(type -> type.isInstance(object));
    }
}
