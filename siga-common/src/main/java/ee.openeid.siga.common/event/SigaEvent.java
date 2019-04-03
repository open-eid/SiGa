package ee.openeid.siga.common.event;

import com.google.common.base.MoreObjects;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.Map;

import static java.time.Instant.now;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.wrap;
import static org.apache.commons.text.StringEscapeUtils.escapeJava;

@Data
@Builder
@FieldDefaults(level = PRIVATE)
public class SigaEvent {
    EventType eventType;
    SigaEventName eventName;
    String clientName;
    String serviceName;
    String serviceUuid;
    String errorCode;
    String errorMessage;
    Long timestamp;
    Long duration;
    EventResultType resultType;
    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    Map<String, String> eventParameters = new HashMap<>();

    @Builder(buildMethodName = "buildEventWithParameter")
    public static SigaEvent buildEventWithParameter(SigaEventName eventName, SigaEventName.EventParam parameterName, String parameterValue) {
        SigaEvent event = SigaEvent.builder()
                .eventType(SigaEvent.EventType.FINISH)
                .eventName(eventName)
                .timestamp(now().toEpochMilli())
                .resultType(EventResultType.SUCCESS)
                .build();
        event.addEventParameter(parameterName, parameterValue);
        return event;
    }

    public void addEventParameter(SigaEventName.EventParam eventParam, String parameterValue) {
        addEventParameter(eventParam.name().toLowerCase(), escapeJava(parameterValue));
    }

    public void addEventParameter(String parameterName, String parameterValue) {
        eventParameters.put(escapeJava(parameterName), escapeJava(parameterValue));
    }

    public String getEventParameter(SigaEventName.EventParam parameterName) {
        return eventParameters.get(parameterName.name().toLowerCase());
    }

    public String getEventParameter(String parameterName) {
        return eventParameters.get(parameterName.toLowerCase());
    }

    public boolean containsParameterWithValue(String parameterValue) {
        return eventParameters.containsValue(parameterValue);
    }

    public void setErrorCode(SigaEventName.ErrorCode errorCode) {
        this.errorCode = errorCode.name();
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper ts = MoreObjects.toStringHelper(this)
                .add("timestamp", timestamp)
                .add("event_type", eventType)
                .add("event_name", eventName)
                .add("client_name", escapeJava(clientName))
                .add("service_name", escapeJava(serviceName))
                .add("service_uuid", serviceUuid);

        if (!eventParameters.isEmpty()) {
            eventParameters.forEach((name, value) -> {
                if (value != null) {
                    if (value.contains(" ")) {
                        ts.add(name, wrap(value, "\""));
                    } else {
                        ts.add(name, value);
                    }
                }
            });
        }

        ts.add("error_code", escapeJava(errorCode));
        ts.add("error_message", errorMessage != null ? wrap(escapeJava(errorMessage), "\"") : null);
        ts.add("duration_ms", duration);
        ts.add("result", resultType);
        return ts.omitNullValues().toString().replace("SigaEvent{", "[").replace("}", "]");
    }

    public enum EventType {
        START, FINISH
    }

    public enum EventResultType {
        SUCCESS, EXCEPTION
    }
}
