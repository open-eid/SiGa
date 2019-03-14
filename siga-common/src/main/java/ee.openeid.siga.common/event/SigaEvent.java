package ee.openeid.siga.common.event;

import com.google.common.base.MoreObjects;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

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
    Long executionTime;
    Long executionDuration;
    EventResultType resultType;
    @Builder.Default
    Map<String, String> eventParameters = new HashMap<>();

    public void addEventParameter(String parameterName, String parameterValue) {
        eventParameters.put(parameterName, parameterValue);
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper ts = MoreObjects.toStringHelper(this)
                .add("event_type", eventType)
                .add("event_name", eventName)
                .add("client_name", escapeJava(clientName))
                .add("service_name", escapeJava(serviceName))
                .add("service_uuid", serviceUuid);
        getParameters(eventParameters).ifPresent(s -> ts.add("event_parameters", s));
        ts.add("error_code", escapeJava(errorCode));
        ts.add("error_message", errorMessage != null ? wrap(escapeJava(errorMessage), "\"") : null);
        ts.add("duration", executionDuration != null ? executionDuration + "ms" : null);
        ts.add("result", resultType);
        return ts.omitNullValues().toString().replace("SigaEvent{", "[").replace("}", "]");
    }

    private Optional<String> getParameters(Map<String, String> parameters) {
        if (!parameters.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ", "[", "]");
            parameters.forEach((name, value) -> {
                if (value.contains(" ")) {
                    sj.add(escapeJava(name) + "=" + wrap(escapeJava(value), "\""));
                } else {
                    sj.add(escapeJava(name) + "=" + escapeJava(value));
                }
            });
            return Optional.of(sj.toString());
        }
        return Optional.empty();
    }

    public enum EventType {
        START, FINISH
    }

    public enum EventResultType {
        SUCCESS, EXCEPTION
    }
}
