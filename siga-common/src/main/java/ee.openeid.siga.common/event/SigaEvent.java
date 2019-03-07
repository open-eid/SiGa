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
    Map<String, String> startParameters = new HashMap<>();
    @Builder.Default
    Map<String, String> returnParameters = new HashMap<>();

    public void addStartParameter(String parameterName, String parameterValue) {
        startParameters.put(parameterName, parameterValue);
    }

    public void addReturnParameter(String parameterName, String parameterValue) {
        returnParameters.put(parameterName, parameterValue);
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("[");
        sb.append("event_type=").append(eventType.name()).append(", ");
        sb.append("client_name=").append(clientName).append(", ");
        sb.append("service_name=").append(serviceName).append(", ");
        sb.append("service_uuid=").append(serviceUuid).append(", ");

        getParameters(startParameters).ifPresent(s -> sb.append("start_parameters=").append(s));
        getParameters(returnParameters).ifPresent(s -> sb.append("return_parameters=").append(s));

        if (errorCode != null) {
            sb.append("error_code=").append(errorCode).append(", ");
        }
        if (executionDuration != null) {
            sb.append("duration=").append(executionDuration).append("ms");
        }
        if (resultType != null) {
            sb.append(", result=").append(resultType.name());
        }
        sb.append("]");
        return sb.toString();
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
