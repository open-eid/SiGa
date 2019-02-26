package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.exception.DuplicateDataFileException;
import eu.europa.esig.dss.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

public class HashCodesDataFileParser {

    private byte[] hashCodesDataFile;
    private Map<String, HashCodesEntry> entries = new HashMap<>();


    public HashCodesDataFileParser(byte[] hashCodesDataFile) {
        this.hashCodesDataFile = hashCodesDataFile;
        loadHashCodesEntries();
    }

    public Map<String, HashCodesEntry> getEntries() {
        return entries;
    }

    private void loadHashCodesEntries() {
        Element root = DomUtils.buildDOM(hashCodesDataFile).getDocumentElement();
        Node child = root.getFirstChild();
        while (child != null) {
            String nodeName = child.getLocalName();
            if ("file-entry".equals(nodeName)) {
                addFileEntry(child);
            }
            child = child.getNextSibling();
        }
    }

    private void addFileEntry(Node child) {
        NamedNodeMap attributes = child.getAttributes();
        String filePath = attributes.getNamedItem("full-path").getTextContent();
        String hash = attributes.getNamedItem("hash").getTextContent();
        String size = attributes.getNamedItem("size").getTextContent();
        validateNotDuplicateFile(filePath);
        HashCodesEntry hashCodesEntry = new HashCodesEntry(hash, Integer.parseInt(size));
        entries.put(filePath, hashCodesEntry);
    }

    private void validateNotDuplicateFile(String filePath) {
        if (entries.containsKey(filePath))
            throw new DuplicateDataFileException("Unable to parse hashcodes datafile");
    }
}
