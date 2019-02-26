package ee.openeid.siga.service.signature.hashcode;


import lombok.Data;

@Data
public class HashCodeDataFileEntry {
    private String hash;
    private String hashAlgo;
}
