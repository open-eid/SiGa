package ee.openeid.siga.common.event;

import com.google.common.base.MoreObjects;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.Map;

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
    Map<String, String> eventParameters = new HashMap<>();

    public void addEventParameter(String parameterName, String parameterValue) {
        eventParameters.put(parameterName, parameterValue);
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
                if (value.contains(" ")) {
                    ts.add(escapeJava(name), wrap(escapeJava(value), "\""));
                } else {
                    ts.add(escapeJava(name), escapeJava(value));
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
