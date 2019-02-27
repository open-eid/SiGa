package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.common.SignatureHashCodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.service.signature.client.ValidationReport;
import ee.openeid.siga.service.signature.client.ValidationResponse;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.HashCodeDataFile;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class RequestUtil {

    private static final String SIGNED_HASHCODE = "hashcode.asice";

    public static List<ee.openeid.siga.common.HashCodeDataFile> createHashCodeDataFiles() {
        List<ee.openeid.siga.common.HashCodeDataFile> hashCodeDataFiles = new ArrayList<>();
        ee.openeid.siga.common.HashCodeDataFile dataFile = new ee.openeid.siga.common.HashCodeDataFile();
        dataFile.setFileName("test.txt");
        dataFile.setFileHashSha256("asdjaosdjasp=");
        dataFile.setFileHashSha512("asdjaosdasdasdasdasdsdadjasp=");
        hashCodeDataFiles.add(dataFile);
        return hashCodeDataFiles;
    }

    public static SignatureWrapper createSignatureWrapper() throws IOException, URISyntaxException {
        SignatureWrapper signatureWrapper = new SignatureWrapper();
        InputStream inputStream = TestUtil.getFileInputStream(SIGNED_HASHCODE);
        signatureWrapper.setSignature(Base64.getEncoder().encode(IOUtils.toByteArray(inputStream)));
        List<SignatureHashCodeDataFile> signatureDataFiles = new ArrayList<>();
        SignatureHashCodeDataFile dataFile = new SignatureHashCodeDataFile();
        dataFile.setFileName("test.txt");
        dataFile.setHashAlgo("SHA256");
        signatureDataFiles.add(dataFile);

        signatureWrapper.setDataFiles(signatureDataFiles);
        return signatureWrapper;
    }

    public static ValidationResponse createValidationResponse() {
        ValidationResponse response = new ValidationResponse();
        ValidationReport validationReport = new ValidationReport();
        ValidationConclusion validationConclusion = new ValidationConclusion();
        validationConclusion.setValidSignaturesCount(1);
        validationConclusion.setSignaturesCount(1);
        validationReport.setValidationConclusion(validationConclusion);
        response.setValidationReport(validationReport);
        return response;
    }

    public static CreateHashCodeContainerRequest getHashCodeCreateContainerRequest() {
        CreateHashCodeContainerRequest request = new CreateHashCodeContainerRequest();
        request.setContainerName("test.asice");
        HashCodeDataFile dataFile1 = new HashCodeDataFile();
        dataFile1.setFileHashSha256("SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms");
        dataFile1.setFileHashSha512("8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw");
        dataFile1.setFileSize(10);
        dataFile1.setFileName("first datafile.txt");
        request.getDataFiles().add(dataFile1);
        HashCodeDataFile dataFile2 = new HashCodeDataFile();
        dataFile2.setFileHashSha256("SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms");
        dataFile2.setFileHashSha512("8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw");
        dataFile2.setFileSize(10);
        dataFile2.setFileName("second datafile.txt");
        request.getDataFiles().add(dataFile2);
        return request;
    }


}
