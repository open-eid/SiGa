package ee.openeid.siga.auth.filter.hmac;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.webapp.json.ErrorResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.*;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.AUTHENTICATION_ERROR;
import static java.time.Instant.ofEpochMilli;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.apache.commons.io.IOUtils.toByteArray;

public class HmacAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private final Long tokenExpirationInSeconds;
    private final Long tokenClockSkew;
    private final SigaEventLogger sigaEventLogger;

    public HmacAuthenticationFilter(SigaEventLogger sigaEventLogger, RequestMatcher requestMatcher, SecurityConfigurationProperties securityConfigurationProperties) {
        super(requestMatcher);
        tokenExpirationInSeconds = securityConfigurationProperties.getHmac().getExpiration();
        tokenClockSkew = securityConfigurationProperties.getHmac().getClockSkew();
        this.sigaEventLogger = sigaEventLogger;
        setAuthenticationSuccessHandler(noRedirectAuthenticationSuccessHandler());
    }

    @Override
    public Authentication attemptAuthentication(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        sigaEventLogger.logStartEvent(SigaEventName.AUTHENTICATION);
        final String timestamp = ofNullable(request.getHeader(X_AUTHORIZATION_TIMESTAMP.getValue())).orElseThrow(() -> new HmacAuthenticationException("Missing X-Authorization-Timestamp header"));
        final String serviceUuid = ofNullable(request.getHeader(X_AUTHORIZATION_SERVICE_UUID.getValue())).orElseThrow(() -> new HmacAuthenticationException("Missing X-Authorization-ServiceUuid header"));
        final String signature = ofNullable(request.getHeader(X_AUTHORIZATION_SIGNATURE.getValue())).orElseThrow(() -> new HmacAuthenticationException("Missing X-Authorization-Signature header"));

        checkIfTokenIsExpired(timestamp);
        String uri = getRequestUri(request);
        String hmacAlgo = getHmacAlgo(request);
        byte[] payload = toByteArray(request.getInputStream());

        HmacSignature token = HmacSignature.builder()
                .macAlgorithm(hmacAlgo)
                .serviceUuid(serviceUuid)
                .timestamp(timestamp)
                .requestMethod(request.getMethod())
                .uri(uri)
                .payload(payload)
                .signature(signature)
                .build();
        return getAuthenticationManager().authenticate(new UsernamePasswordAuthenticationToken(serviceUuid, token, emptyList()));
    }

    private String getHmacAlgo(HttpServletRequest request) {
        String hmacAlgo = request.getHeader(X_AUTHORIZATION_HMAC_ALGORITHM.getValue());
        if (hmacAlgo == null) {
            hmacAlgo = HmacAlgorithm.HMAC_SHA_256.getValue();
        } else if (HmacAlgorithm.fromString(hmacAlgo) == null) {
            throw new HmacAuthenticationException("Invalid HMAC algorithm: " + hmacAlgo);
        }
        return hmacAlgo;
    }

    private String getRequestUri(HttpServletRequest request) {
        String uri = request.getRequestURI().replace(request.getContextPath(), StringUtils.EMPTY);
        if (request.getQueryString() != null) {
            uri += "?" + request.getQueryString();
        }
        return uri;
    }

    private void checkIfTokenIsExpired(String timestamp) {
        if (tokenExpirationInSeconds != -1) {
            HmacSignature.validateTimestamp(timestamp, tokenExpirationInSeconds, tokenClockSkew);
        }
    }

    @Override
    protected void successfulAuthentication(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain, final Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);
        SigaEvent endEvent = sigaEventLogger.logEndEvent(SigaEventName.AUTHENTICATION);
        sigaEventLogger.getFirstMachingEvent(SigaEventName.AUTHENTICATION, SigaEvent.EventType.START).ifPresent(startEvent -> {
            long executionTimeInMilli = Duration.between(ofEpochMilli(startEvent.getTimestamp()), ofEpochMilli(endEvent.getTimestamp())).toMillis();
            endEvent.setDuration(executionTimeInMilli);
        });
        chain.doFilter(request, response);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        SigaEvent exceptionEvent = sigaEventLogger.logExceptionEvent(SigaEventName.AUTHENTICATION);
        exceptionEvent.setErrorCode(AUTHENTICATION_ERROR);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try (OutputStream out = response.getOutputStream()) {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setErrorCode(ErrorResponseCode.AUTHORIZATION_ERROR.name());
            if (failed instanceof InternalAuthenticationServiceException) {
                errorResponse.setErrorMessage("Internal service error");
                exceptionEvent.setErrorMessage("Internal service error");
            } else {
                errorResponse.setErrorMessage(failed.getMessage());
                exceptionEvent.setErrorMessage(failed.getMessage());
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(out, errorResponse);
            out.flush();
        }
    }

    private SimpleUrlAuthenticationSuccessHandler noRedirectAuthenticationSuccessHandler() {
        final SimpleUrlAuthenticationSuccessHandler successHandler = new SimpleUrlAuthenticationSuccessHandler();
        successHandler.setRedirectStrategy((httpServletRequest, httpServletResponse, s) -> {
        });
        return successHandler;
    }
}
