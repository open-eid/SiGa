package ee.openeid.siga.service.signature.test;


import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.service.signature.client.ValidationReport;
import ee.openeid.siga.service.signature.client.ValidationResponse;
import ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRequest;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
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

    public static List<ee.openeid.siga.common.HashcodeDataFile> createHashcodeDataFiles() {
        List<ee.openeid.siga.common.HashcodeDataFile> hashcodeDataFiles = new ArrayList<>();
        ee.openeid.siga.common.HashcodeDataFile dataFile = new ee.openeid.siga.common.HashcodeDataFile();
        dataFile.setFileName("test.txt");
        dataFile.setFileHashSha256("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg");
        dataFile.setFileSize(10);
        dataFile.setFileHashSha512("gRKArS6jBsPLF1VP7aQ8VZ7BA5QA66hj/ntmNcxONZG5899w2VFHg9psyEH4Scg7rPSJQEYf65BGAscMztSXsA");
        hashcodeDataFiles.add(dataFile);
        return hashcodeDataFiles;
    }

    public static SignatureWrapper createSignatureWrapper() throws IOException, URISyntaxException {

        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        hashcodeContainer.open(TestUtil.getFileInputStream(SIGNED_HASHCODE));
        return hashcodeContainer.getSignatures().get(0);
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

    public static CreateHashcodeContainerRequest getHashcodeCreateContainerRequest() {
        CreateHashcodeContainerRequest request = new CreateHashcodeContainerRequest();
        HashcodeDataFile dataFile1 = new HashcodeDataFile();
        dataFile1.setFileHashSha256("SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms");
        dataFile1.setFileHashSha512("8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw");
        dataFile1.setFileSize(10);
        dataFile1.setFileName("first datafile.txt");
        request.getDataFiles().add(dataFile1);
        HashcodeDataFile dataFile2 = new HashcodeDataFile();
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
                .sessionId(CONTAINER_ID)
                .clientName("client1")
                .serviceName("Testimine")
                .serviceUuid("a7fd7728-a3ea-4975-bfab-f240a67e894f")
                .signatures(signatureWrappers)
                .dataFiles(RequestUtil.createHashcodeDataFiles()).build();
    }

    public static SignatureParameters createSignatureParameters(X509Certificate certificate) {
        return createSignatureParameters(certificate, SignatureProfile.LT);
    }

    public static SignatureParameters createSignatureParameters(X509Certificate certificate, SignatureProfile signatureProfile) {
        SignatureParameters signatureParameters = new SignatureParameters();
        signatureParameters.setSigningCertificate(certificate);
        signatureParameters.setSignatureProfile(signatureProfile);
        signatureParameters.setCountry("Estonia");
        signatureParameters.setStateOrProvince("Harjumaa");
        signatureParameters.setCity("Tallinn");
        signatureParameters.setPostalCode("34234");
        signatureParameters.setRoles(Collections.singletonList("Engineer"));
        return signatureParameters;
    }

    public static MobileIdInformation createMobileInformation() {
        MobileIdInformation mobileIdInformation = MobileIdInformation.builder()
                .phoneNo("+37253410832")
                .personIdentifier("3489348234")
                .country("EE")
                .language("EST")
                .relyingPartyName("Testimiseks")
                .messageToDisplay("Random display").build();
        return mobileIdInformation;
    }
}
