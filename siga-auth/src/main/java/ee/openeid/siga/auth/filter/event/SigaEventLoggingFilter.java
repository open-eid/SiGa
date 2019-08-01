package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.auth.filter.hmac.HmacHeader;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;

import static java.time.Instant.ofEpochMilli;

@Component
public class SigaEventLoggingFilter extends AbstractRequestLoggingFilter {

    @Autowired
    private SigaEventLogger sigaEventLogger;

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        SigaEvent event = sigaEventLogger.logStartEvent(SigaEventName.REQUEST);
        String xAuthorizationServiceUuid = request.getHeader(HmacHeader.X_AUTHORIZATION_SERVICE_UUID.getValue());
        event.setServiceUuid(xAuthorizationServiceUuid);
        event.addEventParameter("request_length", Integer.toString(getContentLength(request)));
        event.addEventParameter("request_uri", request.getRequestURI());
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        SigaEvent startRequest = sigaEventLogger.getEvent(0);
        SigaEvent endRequest = sigaEventLogger.logEndEvent(SigaEventName.REQUEST);
        long executionTimeInMilli = Duration.between(ofEpochMilli(startRequest.getTimestamp()), ofEpochMilli(endRequest.getTimestamp())).toMillis();
        endRequest.setDuration(executionTimeInMilli);
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
