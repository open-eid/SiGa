package ee.openeid.siga.common.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.io.Serializable;

@Data
@Builder
public class RelyingPartyInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private String name;
    @NonNull
    private String uuid;
}
