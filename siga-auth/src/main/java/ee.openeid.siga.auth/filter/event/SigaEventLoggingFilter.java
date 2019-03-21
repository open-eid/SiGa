package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.auth.filter.hmac.HmacHeader;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;

import static java.time.Instant.ofEpochMilli;
import static lombok.AccessLevel.PRIVATE;

@Component
@FieldDefaults(level = PRIVATE)
public class SigaEventLoggingFilter extends AbstractRequestLoggingFilter {

    @Autowired
    SigaEventLogger sigaEventLogger;

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        SigaEvent event = sigaEventLogger.logStartEvent(SigaEventName.REQUEST);
        String xAuthorizationServiceUuid = request.getHeader(HmacHeader.X_AUTHORIZATION_SERVICE_UUID.getValue());
        event.setServiceUuid(xAuthorizationServiceUuid);
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        SigaEvent startRequest = sigaEventLogger.getEvent(0);
        SigaEvent endRequest = sigaEventLogger.logEndEvent(SigaEventName.REQUEST);
        long executionTimeInMilli = Duration.between(ofEpochMilli(startRequest.getTimestamp()), ofEpochMilli(endRequest.getTimestamp())).toMillis();
        endRequest.setDuration(executionTimeInMilli);
        sigaEventLogger.logEvents();
    }
}
