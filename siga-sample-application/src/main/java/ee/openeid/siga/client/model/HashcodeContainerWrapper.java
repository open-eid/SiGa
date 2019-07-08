package ee.openeid.siga.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HashcodeContainerWrapper implements Serializable {
    private String id;
    private String fileName;
    @JsonIgnore
    private Map<String, byte[]> originalDataFiles;
    @JsonIgnore
    private transient byte[] hashcodeContainer;
}
