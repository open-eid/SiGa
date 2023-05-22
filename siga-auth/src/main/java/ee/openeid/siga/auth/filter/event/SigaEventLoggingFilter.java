package ee.openeid.siga.auth.filter.event;

import static java.time.Instant.ofEpochMilli;

import java.io.IOException;
import java.time.Duration;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ee.openeid.siga.auth.filter.hmac.HmacHeader;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEvent.EventResultType;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SigaEventLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_LENGTH_PARAM_NAME = "request_length";
    private static final String REQUEST_URI_PARAM_NAME = "request_uri";
    private final SigaEventLogger sigaEventLogger;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isFirstRequest = !isAsyncDispatch(request);
        if (isFirstRequest) {
            beforeRequest(request);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!isAsyncStarted(request)) {
                afterRequest(request, response);
            }
        }
    }

    protected void beforeRequest(HttpServletRequest request) {
        SigaEvent event = sigaEventLogger.logStartEvent(SigaEventName.REQUEST);
        String xAuthorizationServiceUuid = request.getHeader(HmacHeader.X_AUTHORIZATION_SERVICE_UUID.getValue());
        event.setServiceUuid(xAuthorizationServiceUuid);
        event.addEventParameter(REQUEST_LENGTH_PARAM_NAME, Integer.toString(getContentLength(request)));
        event.addEventParameter(REQUEST_URI_PARAM_NAME, request.getRequestURI());
    }

    protected void afterRequest(HttpServletRequest request, HttpServletResponse response) {
        SigaEvent startRequest = sigaEventLogger.getEvent(0);
        SigaEvent endRequest = sigaEventLogger.logEndEvent(SigaEventName.REQUEST);
        long executionTimeInMilli = Duration
                .between(ofEpochMilli(startRequest.getTimestamp()), ofEpochMilli(endRequest.getTimestamp())).toMillis();
        endRequest.setDuration(executionTimeInMilli);
        endRequest.addEventParameter(REQUEST_URI_PARAM_NAME, request.getRequestURI());
        endRequest.setStatusCode(response.getStatus());
        sigaEventLogger.logEvents();
    }

    private int getContentLength(HttpServletRequest request) {
        if (request.getContentLength() != -1) {
            return request.getContentLength();
        } else {
            return 0;
        }
    }
}
