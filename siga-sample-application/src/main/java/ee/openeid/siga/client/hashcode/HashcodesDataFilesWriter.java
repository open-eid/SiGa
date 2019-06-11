package ee.openeid.siga.client.hashcode;

import lombok.SneakyThrows;
import org.digidoc4j.DigestAlgorithm;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.OutputStream;
import java.util.List;

class HashcodesDataFilesWriter {

    public static final String HASHCODES_SHA256 = "META-INF/hashcodes-sha256.xml";
    public static final String HASHCODES_SHA512 = "META-INF/hashcodes-sha512.xml";
    public static final String HASHCODES_PREFIX = "META-INF/hashcodes-";
    private final DigestAlgorithm digestAlgorithm;
    private Document dom;
    private Element rootElement;

    public HashcodesDataFilesWriter(DigestAlgorithm digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    @SneakyThrows
    public void generateHashcodeFile(List<HashcodeDataFile> dataFiles) {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        dom = documentBuilder.newDocument();
        rootElement = dom.createElement("hashcodes");
        dataFiles.forEach(this::addFileEntry);
        dom.appendChild(rootElement);
    }

    public void writeTo(OutputStream outputStream) {
        DOMImplementationLS implementation = (DOMImplementationLS) dom.getImplementation();
        LSOutput lsOutput = implementation.createLSOutput();
        lsOutput.setByteStream(outputStream);
        LSSerializer writer = implementation.createLSSerializer();
        writer.write(dom, lsOutput);
    }

    private void addFileEntry(HashcodeDataFile dataFile) {
        Element child = dom.createElement("file-entry");
        child.setAttribute("full-path", dataFile.getFileName());
        if (DigestAlgorithm.SHA256 == digestAlgorithm) {
            child.setAttribute("hash", dataFile.getFileHashSha256());
        } else if (DigestAlgorithm.SHA512 == digestAlgorithm) {
            child.setAttribute("hash", dataFile.getFileHashSha512());
        }
        child.setAttribute("size", dataFile.getFileSize().toString());
        rootElement.appendChild(child);
    }
}
