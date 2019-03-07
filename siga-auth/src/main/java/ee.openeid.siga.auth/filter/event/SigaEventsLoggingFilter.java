package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.common.event.SigaEventType;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import javax.servlet.http.HttpServletRequest;

@CommonsLog
@Component
public class SigaEventsLoggingFilter extends AbstractRequestLoggingFilter {

    @Autowired
    SigaEventLogger sigaEventLogger;

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        sigaEventLogger.logStartEvent(SigaEventType.REQUEST);
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        sigaEventLogger.logEndEvent(SigaEventType.REQUEST);
        sigaEventLogger.logEvents();
    }
}
