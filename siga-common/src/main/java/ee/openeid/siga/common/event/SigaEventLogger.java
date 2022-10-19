package ee.openeid.siga.common.event;

import ee.openeid.siga.common.auth.SigaUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.time.Instant.now;
import static java.time.Instant.ofEpochMilli;
import static org.slf4j.MarkerFactory.getMarker;

@Slf4j
@Component
public class SigaEventLogger {
    public static final String SIGA_EVENT = "SIGA_EVENT";

    private final ThreadLocal<List<SigaEvent>> threadScopeEvents = ThreadLocal.withInitial(ArrayList::new);

    public Optional<SigaEvent> getFirstMachingEvent(SigaEventName eventName, SigaEvent.EventType eventType) {
        for (SigaEvent e : threadScopeEvents.get()) {
            if (eventName.equals(e.getEventName()) && eventType.equals(e.getEventType())) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public Optional<SigaEvent> getLastMachingEvent(Predicate<SigaEvent> predicate) {
        List<SigaEvent> events = threadScopeEvents.get();
        for (int i = events.size(); i-- > 0; ) {
            SigaEvent e = events.get(i);
            if (predicate.test(e)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public Optional<SigaEvent> getFirstMachingEventAfter(SigaEvent afterEvent, Predicate<SigaEvent> predicate) {
        boolean startSearch = false;
        List<SigaEvent> events = threadScopeEvents.get();
        for (SigaEvent e : events) {
            if (afterEvent.equals(e)) {
                startSearch = true;
                continue;
            }
            if (startSearch && predicate.test(e)) {
                return Optional.of(e);
            }
            if (e.getEventName().equals(afterEvent.getEventName())) {
                break;
            }
        }
        return Optional.empty();
    }

    public SigaEvent getEvent(int index) {
        List<SigaEvent> events = threadScopeEvents.get();
        if (index >= events.size()) {
            return null;
        }
        return events.get(index);
    }

    public void logEvent(SigaEvent event) {
        threadScopeEvents.get().add(event);
    }

    public SigaEvent logStartEvent(SigaEventName eventName) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.START)
                .eventName(eventName)
                .timestamp(now().toEpochMilli())
                .build();
        threadScopeEvents.get().add(event);
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

    public SigaEvent logExceptionEventForIntermediateEvents(SigaEvent startEvent, SigaEventName.ErrorCode errorCode, String errorMessage) {
        return logExceptionEventFor(startEvent, errorCode, errorMessage, true);
    }

    public SigaEvent logExceptionEventFor(SigaEvent startEvent, SigaEventName.ErrorCode errorCode, String errorMessage) {
        return logExceptionEventFor(startEvent, errorCode, errorMessage, false);
    }

    private SigaEvent logExceptionEventFor(SigaEvent startEvent, SigaEventName.ErrorCode errorCode, String errorMessage, boolean includeIntermediateEvents) {
        SigaEvent endEvent = logExceptionEvent(startEvent.getEventName());
        long executionTimeInMilli = Duration.between(ofEpochMilli(startEvent.getTimestamp()), ofEpochMilli(endEvent.getTimestamp())).toMillis();
        endEvent.setErrorCode(errorCode);
        endEvent.setErrorMessage(errorMessage);
        endEvent.setDuration(executionTimeInMilli);

        if (includeIntermediateEvents) {
            boolean markFailed = false;
            for (SigaEvent e : threadScopeEvents.get()) {
                if (e.equals(startEvent)) {
                    markFailed = true;
                    continue;
                }
                if (markFailed) {
                    e.setErrorCode(errorCode);
                    e.setErrorMessage(errorMessage);
                    e.setResultType(SigaEvent.EventResultType.EXCEPTION);
                }
                if (e.equals(endEvent)) {
                    break;
                }
            }
        }

        return endEvent;
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
        threadScopeEvents.get().add(event);
        return event;
    }

    public SigaEvent logEndEventFor(SigaEvent startEvent) {
        SigaEvent endEvent = logEndEvent(startEvent.getEventName());
        long executionTimeInMilli = Duration.between(ofEpochMilli(startEvent.getTimestamp()), ofEpochMilli(endEvent.getTimestamp())).toMillis();
        endEvent.setDuration(executionTimeInMilli);
        return endEvent;
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
        threadScopeEvents.get().add(event);
        return event;
    }

    public void logEvents() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            List<SigaEvent> events = threadScopeEvents.get();
            if (authentication != null && authentication.isAuthenticated()) {
                SigaUserDetails sud = (SigaUserDetails) authentication.getPrincipal();
                insertClientAndServiceDataToEvents(sud);
                events.forEach(e -> log.info(getMarker(SIGA_EVENT), e.toString()));
            } else {
                if (!events.isEmpty()) {
                    final String serviceUuid = events.get(0).getServiceUuid();
                    events.forEach(e -> {
                        e.setServiceUuid(serviceUuid);
                        log.info(getMarker(SIGA_EVENT), e.toString());
                    });
                }
            }
        } catch (Exception ex) {
            log.error("Error logging SiGa events", ex);
        } finally {
            threadScopeEvents.remove();
        }
    }

    private void insertClientAndServiceDataToEvents(SigaUserDetails sud) {
        final String clientName = sud.getClientName();
        final String clientUuid = sud.getClientUuid();
        final String serviceName = sud.getServiceName();
        final String serviceUuid = sud.getServiceUuid();
        List<SigaEvent> events = threadScopeEvents.get();
        events.forEach(e -> {
            e.setClientName(clientName);
            e.setClientUuid(clientUuid);
            e.setServiceName(serviceName);
            e.setServiceUuid(serviceUuid);
        });
    }
}
