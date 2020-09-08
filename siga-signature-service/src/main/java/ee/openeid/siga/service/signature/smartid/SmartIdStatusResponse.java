package ee.openeid.siga.service.signature.smartid;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder
public class SmartIdStatusResponse {

    @NonNull
    private final SmartIdSessionStatus status;

    private final byte[] signature;
}
