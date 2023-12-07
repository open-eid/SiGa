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

@RequiredArgsConstructor
public class HttpClientImpl implements HttpGetClient, HttpPostClient {
    @NonNull
    private final WebClient webClient;

    @Override
    public <T> T get(String path, Class<T> responseType) {
        return webClient.get()
                .uri(uriBuilder(path))
                .exchangeToMono(clientResponseHandler(responseType))
                .block();
    }

    @Override
    public <T> T post(String path, Object requestBody, Class<T> responseType) {
        return webClient.post()
                .uri(uriBuilder(path))
                .bodyValue(requestBody)
                .exchangeToMono(clientResponseHandler(responseType))
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
}
