package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.webapp.json.DataFile;
import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DigestDocument;
import eu.europa.esig.dss.MimeType;
import lombok.Getter;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class HashCodeContainer {

    private List<DataFile> dataFiles = new ArrayList<>();

    public void save(OutputStream outputStream) {
        HashCodeContainerCreator hashCodeContainerCreator = new HashCodeContainerCreator(outputStream);
        hashCodeContainerCreator.writeMimeType();
        hashCodeContainerCreator.writeManifest(convertDataFiles());
        hashCodeContainerCreator.writeHashCodeFiles(dataFiles);
        hashCodeContainerCreator.finalizeZipFile();
    }

    private List<org.digidoc4j.DataFile> convertDataFiles() {
        return dataFiles.stream().map(d -> {
            DSSDocument dssDocument = new DigestDocument();
            dssDocument.setMimeType(MimeType.BINARY);
            dssDocument.setName(d.getFileName());
            org.digidoc4j.DataFile dataFile = new org.digidoc4j.DataFile();
            dataFile.setDocument(dssDocument);
            return dataFile;

        }).collect(Collectors.toList());
    }
}
