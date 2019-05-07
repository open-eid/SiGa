package ee.openeid.siga.client.hashcode;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HashcodesEntry {
    private String hash;
    private int size;
}
