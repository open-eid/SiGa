package ee.openeid.siga.common.event;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
@FieldDefaults(level = PRIVATE)
public class SigaEvent {
    SigaEventType eventType;
    String clientName;
    String serviceName;
    String serviceUuid;
    String errorCode;
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
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        sj.add("event_type=" + eventType.name());
        sj.add("client_name=" + clientName);
        sj.add("service_name=" + serviceName);
        sj.add("service_uuid=" + serviceUuid);
        getParameters(eventParameters).ifPresent(s -> sj.add("event_parameters=" + s));

        if (errorCode != null) {
            sj.add("error_code=" + errorCode);
        }
        if (executionDuration != null) {
            sj.add("duration=" + executionDuration + "ms");
        }
        if (resultType != null) {
            sj.add("result=" + resultType.name());
        }
        return sj.toString();
    }

    private Optional<String> getParameters(Map<String, String> parameters) {
        if (!parameters.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ", "[", "]");
            parameters.forEach((parameterName, parameterValue) -> {
                if (parameterValue.contains(" ")) {
                    parameterValue = StringUtils.quote(parameterValue);
                }
                sj.add(parameterName + "=" + parameterValue);
            });
            return Optional.of(sj.toString());
        }
        return Optional.empty();
    }

    public enum EventResultType {
        SUCCESS, EXCEPTION
    }
}
