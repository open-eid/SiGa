package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.service.signature.client.ValidationReport;
import ee.openeid.siga.service.signature.client.ValidationResponse;
import ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.HashCodeDataFile;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.SignatureProfile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RequestUtil {

    public static final String SIGNED_HASHCODE = "hashcode.asice";
    public static final String CONTAINER_ID = "23423423-234234234-324234-4234";

    public static List<ee.openeid.siga.common.HashCodeDataFile> createHashCodeDataFiles() {
        List<ee.openeid.siga.common.HashCodeDataFile> hashCodeDataFiles = new ArrayList<>();
        ee.openeid.siga.common.HashCodeDataFile dataFile = new ee.openeid.siga.common.HashCodeDataFile();
        dataFile.setFileName("test.txt");
        dataFile.setFileHashSha256("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg");
        dataFile.setFileSize(10);
        dataFile.setFileHashSha512("gRKArS6jBsPLF1VP7aQ8VZ7BA5QA66hj/ntmNcxONZG5899w2VFHg9psyEH4Scg7rPSJQEYf65BGAscMztSXsA");
        hashCodeDataFiles.add(dataFile);
        return hashCodeDataFiles;
    }

    public static SignatureWrapper createSignatureWrapper() throws IOException, URISyntaxException {

        DetachedDataFileContainer hashCodeContainer = new DetachedDataFileContainer();
        hashCodeContainer.open(TestUtil.getFileInputStream(SIGNED_HASHCODE));
        return hashCodeContainer.getSignatures().get(0);
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

    public static DetachedDataFileContainerSessionHolder createSessionHolder() throws IOException, URISyntaxException {
        List<SignatureWrapper> signatureWrappers = new ArrayList<>();
        signatureWrappers.add(RequestUtil.createSignatureWrapper());
        return DetachedDataFileContainerSessionHolder.builder()
                .signatures(signatureWrappers)
                .dataFiles(RequestUtil.createHashCodeDataFiles()).build();
    }

    public static SignatureParameters createSignatureParameters(X509Certificate certificate) {
        SignatureParameters signatureParameters = new SignatureParameters();
        signatureParameters.setSigningCertificate(certificate);
        signatureParameters.setSignatureProfile(SignatureProfile.LT);
        signatureParameters.setCountry("Estonia");
        signatureParameters.setStateOrProvince("Harjumaa");
        signatureParameters.setCity("Tallinn");
        signatureParameters.setPostalCode("34234");
        signatureParameters.setRoles(Collections.singletonList("Engineer"));
        return signatureParameters;
    }

    public static MobileIdInformation createMobileInformation() {
        MobileIdInformation mobileIdInformation = new MobileIdInformation();
        mobileIdInformation.setServiceName("Service name");
        mobileIdInformation.setPhoneNo("+37253410832");
        mobileIdInformation.setPersonIdentifier("3489348234");
        mobileIdInformation.setCountry("EE");
        mobileIdInformation.setLanguage("EST");
        mobileIdInformation.setMessageToDisplay("Random display");
        return mobileIdInformation;
    }
}
