package ee.openeid.siga.common.event;

import ch.qos.logback.classic.Level;
import ee.openeid.siga.common.auth.SigaUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.time.Instant.now;
import static java.time.Instant.ofEpochMilli;
import static org.slf4j.MarkerFactory.getMarker;

@Component
@RequestScope
@Slf4j
public class SigaEventLogger implements InitializingBean, DisposableBean {
    public static final String SIGA_EVENT = "SIGA_EVENT";
    private final List<SigaEvent> events = new ArrayList<>();
    private final List<LogObserver> logObservers = new ArrayList<>();

    public Optional<SigaEvent> getFirstMachingEvent(SigaEventName eventName, SigaEvent.EventType eventType) {
        for (SigaEvent e : events) {
            if (eventName.equals(e.getEventName()) && eventType.equals(e.getEventType())) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public Optional<SigaEvent> getLastMachingEvent(Predicate<SigaEvent> predicate) {
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
        if (index >= events.size()) {
            return null;
        }
        return events.get(index);
    }

    public void logEvent(SigaEvent event) {
        events.add(event);
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
            for (SigaEvent e : events) {
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
        events.add(event);
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
        events.add(event);
        return event;
    }

    public void logEvents() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            SigaUserDetails sud = (SigaUserDetails) authentication.getPrincipal();
            final String clientName = sud.getClientName();
            final String clientUuid = sud.getClientUuid();
            final String serviceName = sud.getServiceName();
            final String serviceUuid = sud.getServiceUuid();
            events.stream().peek(e -> {
                e.setClientName(clientName);
                e.setClientUuid(clientUuid);
                e.setServiceName(serviceName);
                e.setServiceUuid(serviceUuid);
            }).forEach(e -> log.info(getMarker(SIGA_EVENT), e.toString()));
        } else {
            if (!events.isEmpty()) {
                final String serviceUuid = events.get(0).getServiceUuid();
                events.forEach(e -> {
                    e.setServiceUuid(serviceUuid);
                    log.info(getMarker(SIGA_EVENT), e.toString());
                });
            }
        }
    }

    @Override
    public void afterPropertiesSet() {
        logObservers.add(LogObserver.buildForSkDataLoader(this, Level.DEBUG));
    }

    @Override
    public void destroy() {
        logObservers.forEach(LogObserver::detatch);
    }
}
