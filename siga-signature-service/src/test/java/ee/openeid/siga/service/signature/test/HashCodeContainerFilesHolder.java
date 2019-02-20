package ee.openeid.siga.service.signature.test;

import lombok.Data;

@Data
public class HashCodeContainerFilesHolder {
    private String mimeTypeContent;
    private String manifestContent;
    private String hashCodesSha256Content;
    private String hashCodesSha512Content;
}
