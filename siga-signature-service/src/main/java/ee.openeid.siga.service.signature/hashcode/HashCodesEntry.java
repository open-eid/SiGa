package ee.openeid.siga.service.signature.hashcode;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HashCodesEntry {
    private String hash;
    private Integer size;
}
