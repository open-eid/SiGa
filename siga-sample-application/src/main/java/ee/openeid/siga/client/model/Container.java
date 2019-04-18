package ee.openeid.siga.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Container implements Serializable {
    private String id;
    private String fileName;
    @JsonIgnore
    private transient byte[] file;
}
