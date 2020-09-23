package ee.openeid.siga.common.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class RelyingPartyInfo {
    @NonNull
    private String name;
    @NonNull
    private String uuid;
}
