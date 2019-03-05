package ee.openeid.siga.common.event;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
@FieldDefaults(level = PRIVATE)
public class Event {
    String eventType;
    String clientName;
    String serviceName;
    String serviceUuid;
}
