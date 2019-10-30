package ee.openeid.siga.service.signature.client;

import lombok.Data;

@Data
public class SivaErrorResponse {
    private String key;
    private String message;
}
