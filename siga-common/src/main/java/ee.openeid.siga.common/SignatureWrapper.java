package ee.openeid.siga.common;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SignatureWrapper {
    private byte[] signature;
    private List<SignatureHashCodeDataFile> dataFiles = new ArrayList<>();
}
