package ee.openeid.siga.common.event;

import ee.openeid.siga.common.auth.SigaUserDetails;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;
import static org.slf4j.MarkerFactory.getMarker;

@Component
@RequestScope
@Slf4j
@FieldDefaults(level = PRIVATE)
public class SigaEventLogger {
    final List<SigaEvent> events = new ArrayList<>();

    public Optional<SigaEvent> getFirstMachingEvent(SigaEventName eventName, SigaEvent.EventType eventType) {
        for (SigaEvent e : events) {
            if (eventName.equals(e.getEventName()) && eventType.equals(e.getEventType())) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public SigaEvent getEvent(int index) {
        return events.get(index);
    }

    public SigaEvent logStartEvent(SigaEventName eventName) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.START)
                .eventName(eventName)
                .timestamp(now().toEpochMilli())
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
        return logExceptionEvent(eventName, null, errorMessage, executionTimeInMilli);
    }

    public SigaEvent logExceptionEvent(SigaEventName eventName, String errorCode, String errorMessage, Long executionTimeInMilli) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.FINISH)
                .eventName(eventName)
                .timestamp(now().toEpochMilli())
                .duration(executionTimeInMilli)
                .errorCode(errorCode)
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
                .timestamp(now().toEpochMilli())
                .duration(executionTimeInMilli)
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
            }).forEach(e -> log.info(getMarker("SIGA_EVENT"), e.toString()));
        } else {
            if (!events.isEmpty()) {
                final String serviceUuid = events.get(0).getServiceUuid();
                events.forEach(e -> {
                    e.setServiceUuid(serviceUuid);
                    log.info(getMarker("SIGA_EVENT"), e.toString());
                });
            }
        }
    }

}
