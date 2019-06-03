package ee.openeid.siga;

import ee.openeid.siga.common.DataFile;
import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.util.CertificateUtil;
import ee.openeid.siga.webapp.json.Signature;
import ee.openeid.siga.webapp.json.SignatureProductionPlace;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.SignatureProfile;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

class RequestTransformer {

    static List<ee.openeid.siga.common.HashcodeDataFile> transformHashcodeDataFilesForApplication(List<ee.openeid.siga.webapp.json.HashcodeDataFile> requestHashcodeDataFiles) {
        List<HashcodeDataFile> hashcodeDataFiles = new ArrayList<>();
        requestHashcodeDataFiles.forEach(
                requestHashcodeDataFile -> {
                    HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
                    hashcodeDataFile.setFileName(requestHashcodeDataFile.getFileName());
                    hashcodeDataFile.setFileSize(requestHashcodeDataFile.getFileSize());
                    hashcodeDataFile.setFileHashSha256(requestHashcodeDataFile.getFileHashSha256());
                    hashcodeDataFile.setFileHashSha512(requestHashcodeDataFile.getFileHashSha512());
                    hashcodeDataFiles.add(hashcodeDataFile);
                }
        );
        return hashcodeDataFiles;
    }

    static List<DataFile> transformDataFilesForApplication(List<ee.openeid.siga.webapp.json.DataFile> requestDataFiles) {
        List<DataFile> dataFiles = new ArrayList<>();
        requestDataFiles.forEach(
                requestDataFile -> {
                    DataFile dataFile = new DataFile();
                    dataFile.setFileName(requestDataFile.getFileName());
                    dataFile.setContent(requestDataFile.getFileContent());
                    dataFiles.add(dataFile);
                });
        return dataFiles;
    }

    static List<Signature> transformSignaturesForResponse(List<ee.openeid.siga.common.Signature> requestSignatures) {
        List<Signature> signatures = new ArrayList<>();
        requestSignatures.forEach(
                requestSignature -> {
                    Signature signature = new Signature();
                    signature.setId(requestSignature.getId());
                    signature.setGeneratedSignatureId(requestSignature.getGeneratedSignatureId());
                    signature.setSignatureProfile(requestSignature.getSignatureProfile());
                    signature.setSignerInfo(requestSignature.getSignerInfo());
                    signatures.add(signature);
                });
        return signatures;
    }

    static List<ee.openeid.siga.webapp.json.HashcodeDataFile> transformHashcodeDataFilesForResponse(List<HashcodeDataFile> requestHashcodeDataFiles) {
        List<ee.openeid.siga.webapp.json.HashcodeDataFile> hashcodeDataFiles = new ArrayList<>();
        requestHashcodeDataFiles.forEach(
                requestHashcodeDataFile -> {
                    ee.openeid.siga.webapp.json.HashcodeDataFile hashcodeDataFile = new ee.openeid.siga.webapp.json.HashcodeDataFile();
                    hashcodeDataFile.setFileName(requestHashcodeDataFile.getFileName());
                    hashcodeDataFile.setFileSize(hashcodeDataFile.getFileSize());
                    hashcodeDataFile.setFileHashSha256(hashcodeDataFile.getFileHashSha256());
                    hashcodeDataFile.setFileHashSha512(hashcodeDataFile.getFileHashSha512());
                    hashcodeDataFiles.add(hashcodeDataFile);
                }
        );
        return hashcodeDataFiles;
    }

    static List<ee.openeid.siga.webapp.json.DataFile> transformDataFilesForResponse(List<DataFile> requestDataFiles) {
        List<ee.openeid.siga.webapp.json.DataFile> dataFiles = new ArrayList<>();
        requestDataFiles.forEach(
                requestHashcodeDataFile -> {
                    ee.openeid.siga.webapp.json.DataFile dataFile = new ee.openeid.siga.webapp.json.DataFile();
                    dataFile.setFileName(requestHashcodeDataFile.getFileName());
                    dataFile.setFileContent(requestHashcodeDataFile.getContent());
                    dataFiles.add(dataFile);
                }
        );
        return dataFiles;
    }

    static SignatureParameters transformRemoteRequest(String signingCertificate, String requestSignatureProfile, SignatureProductionPlace signatureProductionPlace, List<String> roles) {
        SignatureParameters signatureParameters = new SignatureParameters();
        byte[] base64DecodedCertificate = Base64.getDecoder().decode(signingCertificate.getBytes());
        X509Certificate x509Certificate = CertificateUtil.createX509Certificate(base64DecodedCertificate);

        signatureParameters.setSigningCertificate(x509Certificate);
        SignatureProfile signatureProfile = SignatureProfile.findByProfile(requestSignatureProfile);
        signatureParameters.setSignatureProfile(signatureProfile);
        if (signatureProductionPlace != null) {
            signatureParameters.setCountry(signatureProductionPlace.getCountryName());
            signatureParameters.setStateOrProvince(signatureProductionPlace.getStateOrProvince());
            signatureParameters.setCity(signatureProductionPlace.getCity());
            signatureParameters.setPostalCode(signatureProductionPlace.getPostalCode());
        }
        signatureParameters.setRoles(roles);
        return signatureParameters;
    }

    static SignatureParameters transformMobileIdSignatureParameters(String requestSignatureProfile, SignatureProductionPlace signatureProductionPlace, List<String> roles) {
        SignatureParameters signatureParameters = new SignatureParameters();
        SignatureProfile signatureProfile = SignatureProfile.findByProfile(requestSignatureProfile);
        signatureParameters.setSignatureProfile(signatureProfile);
        if (signatureProductionPlace != null) {
            signatureParameters.setCountry(signatureProductionPlace.getCountryName());
            signatureParameters.setStateOrProvince(signatureProductionPlace.getStateOrProvince());
            signatureParameters.setCity(signatureProductionPlace.getCity());
            signatureParameters.setPostalCode(signatureProductionPlace.getPostalCode());
        }
        signatureParameters.setRoles(roles);
        return signatureParameters;

    }

    static MobileIdInformation transformMobileIdInformation(String language, String messageToDisplay, String personIdentifier, String phoneNo) {
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return MobileIdInformation.builder()
                .language(language)
                .messageToDisplay(messageToDisplay)
                .personIdentifier(personIdentifier)
                .phoneNo(phoneNo)
                .relyingPartyName(sigaUserDetails.getSkRelyingPartyName()).build();
    }

}
