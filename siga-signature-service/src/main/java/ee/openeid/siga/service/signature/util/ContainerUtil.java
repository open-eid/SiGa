package ee.openeid.siga.service.signature.util;

import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.SignatureHashcodeDataFile;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class ContainerUtil {

    private ContainerUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static void addSignatureDataFilesEntries(HashcodeSignatureWrapper wrapper, Map<String, String> dataFiles) {
        dataFiles.forEach((fileName, fileHashAlgo) -> {
            SignatureHashcodeDataFile hashcodeDataFile = new SignatureHashcodeDataFile();
            hashcodeDataFile.setFileName(fileName);
            hashcodeDataFile.setHashAlgo(fileHashAlgo);
            wrapper.getDataFiles().add(hashcodeDataFile);
        });
    }

    public static Container createContainer(byte[] container, Configuration configuration) {
        return ContainerBuilder
                .aContainer(Container.DocumentType.BDOC)
                .withConfiguration(configuration)
                .fromStream(new ByteArrayInputStream(container))
                .build();
    }
}
