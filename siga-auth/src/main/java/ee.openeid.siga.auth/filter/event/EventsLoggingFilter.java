package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.auth.filter.hmac.HmacHeaders;
import ee.openeid.siga.common.event.Event;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import javax.servlet.http.HttpServletRequest;

@CommonsLog
@Component
public class EventsLoggingFilter extends AbstractRequestLoggingFilter {

    @Autowired
    EventLogger eventLogger;

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        String uuid = request.getHeader(HmacHeaders.X_AUTHORIZATION_SERVICE_UUID.getValue());
        eventLogger.getEvents().add(Event.builder().serviceUuid(uuid).eventType("BEFORE_REQUEST").build());
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        String uuid = request.getHeader(HmacHeaders.X_AUTHORIZATION_SERVICE_UUID.getValue());
        eventLogger.getEvents().add(Event.builder().serviceUuid(uuid).eventType("AFTER_REQUEST").build());
        eventLogger.logEvents();
    }
}
