package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.auth.model.SigaUserDetails;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventName;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;

import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;

@Component
@RequestScope
@CommonsLog
@FieldDefaults(level = PRIVATE)
public class SigaEventLogger {
    final List<SigaEvent> events = new ArrayList<>();

    public SigaEvent getEvent(int index) {
        return events.get(index);
    }

    public SigaEvent logStartEvent(SigaEventName eventName) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.START)
                .eventName(eventName)
                .executionTime(now().toEpochMilli())
                .build();
        events.add(event);
        return event;
    }

    public SigaEvent logExceptionEvent(SigaEventName eventName) {
        return logExceptionEvent(eventName, null);
    }

    public SigaEvent logExceptionEvent(SigaEventName eventName, Long executionTimeInMilli) {
        return logExceptionEvent(eventName, null, executionTimeInMilli);
    }

    public SigaEvent logExceptionEvent(SigaEventName eventName, String errorMessage, Long executionTimeInMilli) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.FINISH)
                .eventName(eventName)
                .executionTime(now().toEpochMilli())
                .executionDuration(executionTimeInMilli)
                .errorMessage(errorMessage)
                .resultType(SigaEvent.EventResultType.EXCEPTION)
                .build();
        events.add(event);
        return event;
    }

    public SigaEvent logEndEvent(SigaEventName eventName) {
        return logEndEvent(eventName, null);
    }

    public SigaEvent logEndEvent(SigaEventName eventName, Long executionTimeInMilli) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.FINISH)
                .eventName(eventName)
                .executionTime(now().toEpochMilli())
                .executionDuration(executionTimeInMilli)
                .resultType(SigaEvent.EventResultType.SUCCESS)
                .build();
        events.add(event);
        return event;
    }

    public void logEvents() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            SigaUserDetails sud = (SigaUserDetails) authentication.getPrincipal();
            final String clientName = sud.getClientName();
            final String serviceName = sud.getServiceName();
            final String serviceUuid = sud.getServiceUuid();
            events.stream().peek(e -> {
                e.setClientName(clientName);
                e.setServiceName(serviceName);
                e.setServiceUuid(serviceUuid);
            }).forEach(log::info);
        } else {
            if (!events.isEmpty()) {
                final String serviceUuid = events.get(0).getServiceUuid();
                events.forEach(e -> {
                    e.setServiceUuid(serviceUuid);
                    log.info(e);
                });
            }
        }
    }

}
