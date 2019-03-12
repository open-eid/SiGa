package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.auth.filter.hmac.HmacHeaders;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventType;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import javax.servlet.http.HttpServletRequest;

import static lombok.AccessLevel.PRIVATE;

@CommonsLog
@Component
@FieldDefaults(level = PRIVATE)
public class SigaEventLoggingFilter extends AbstractRequestLoggingFilter {

    @Autowired
    SigaEventLogger sigaEventLogger;

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        SigaEvent event = sigaEventLogger.logStartEvent(SigaEventType.REQUEST);
        String xAuthorizationServiceUuid = request.getHeader(HmacHeaders.X_AUTHORIZATION_SERVICE_UUID.getValue());
        event.setServiceUuid(xAuthorizationServiceUuid);
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        sigaEventLogger.logEndEvent(SigaEventType.REQUEST);
        sigaEventLogger.logEvents();
    }
}
