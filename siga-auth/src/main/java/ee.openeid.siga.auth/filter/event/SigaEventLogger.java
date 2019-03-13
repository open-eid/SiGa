package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.auth.model.SigaUserDetails;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventName;
import lombok.Getter;
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
    @Getter
    final List<SigaEvent> events = new ArrayList<>();

    public SigaEvent logStartEvent(SigaEventName eventType) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.START)
                .eventName(eventType)
                .executionTime(now().toEpochMilli())
                .build();
        events.add(event);
        return event;
    }

    public SigaEvent logExceptionEvent(SigaEventName eventType) {
        return logExceptionEvent(eventType, null);
    }

    public SigaEvent logExceptionEvent(SigaEventName eventType, Long executionTimeInMilli) {
        return logExceptionEvent(eventType, null, executionTimeInMilli);
    }

    public SigaEvent logExceptionEvent(SigaEventName eventType, String errorMessage, Long executionTimeInMilli) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.FINISH)
                .eventName(eventType)
                .executionTime(now().toEpochMilli())
                .executionDuration(executionTimeInMilli)
                .errorMessage(errorMessage)
                .resultType(SigaEvent.EventResultType.EXCEPTION)
                .build();
        events.add(event);
        return event;
    }

    public SigaEvent logEndEvent(SigaEventName eventType) {
        return logEndEvent(eventType, null);
    }

    public SigaEvent logEndEvent(SigaEventName eventType, Long executionTimeInMilli) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.FINISH)
                .eventName(eventType)
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
            events.stream().map(e -> {
                e.setClientName(clientName);
                e.setServiceName(serviceName);
                e.setServiceUuid(serviceUuid);
                return e;
            }).forEach(log::info);
        } else {
            final String serviceUuid = getEvents().get(0).getServiceUuid();
            events.forEach(e -> {
                e.setServiceUuid(serviceUuid);
                log.info(e);
            });
        }
    }

}
