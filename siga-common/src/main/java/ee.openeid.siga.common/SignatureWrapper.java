package ee.openeid.siga.common;

import lombok.Data;
import org.digidoc4j.Signature;

import java.util.ArrayList;
import java.util.List;

@Data
public class SignatureWrapper {
    private Signature signature;
    private List<SignatureHashCodeDataFile> dataFiles = new ArrayList<>();
}
