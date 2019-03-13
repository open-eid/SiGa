package ee.openeid.siga.common.event;

import com.google.common.base.MoreObjects;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static lombok.AccessLevel.PRIVATE;

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
        MoreObjects.ToStringHelper toStringHelper = MoreObjects.toStringHelper(this)
                .add("event_type", eventType)
                .add("event_name", eventName)
                .add("client_name", clientName)
                .add("service_name", serviceName)
                .add("service_uuid", serviceUuid);
        getParameters(eventParameters).ifPresent(s -> toStringHelper.add("event_parameters", s));
        toStringHelper.add("error_code", errorCode);
        toStringHelper.add("error_message", errorMessage != null ? StringUtils.wrap(errorMessage, "\"") : null);
        toStringHelper.add("duration", executionDuration != null ? executionDuration + "ms" : null);
        toStringHelper.add("result", resultType);
        return toStringHelper.omitNullValues().toString().replace("SigaEvent{", "[").replace("}", "]");
    }

    private Optional<String> getParameters(Map<String, String> parameters) {
        if (!parameters.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ", "[", "]");
            parameters.forEach((parameterName, parameterValue) -> {
                if (parameterValue.contains(" ")) {
                    parameterValue = StringUtils.wrap(parameterValue, "\"");
                }
                sj.add(parameterName + "=" + parameterValue);
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
