package ee.openeid.siga;

import ee.openeid.siga.common.exception.InvalidCertificateException;
import ee.openeid.siga.common.model.DataFile;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.util.CertificateUtil;
import ee.openeid.siga.util.SupportedCertificateEncoding;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.Signature;
import ee.openeid.siga.webapp.json.SignatureProductionPlace;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.exceptions.InvalidSignatureException;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class RequestTransformer {

    private RequestTransformer() {
        throw new IllegalStateException("Utility class");
    }

    static List<HashcodeDataFile> transformHashcodeDataFilesForApplication(List<ee.openeid.siga.webapp.json.HashcodeDataFile> requestHashcodeDataFiles) {
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

    static List<Signature> transformSignaturesForResponse(List<ee.openeid.siga.common.model.Signature> requestSignatures) {
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
                    hashcodeDataFile.setFileSize(requestHashcodeDataFile.getFileSize());
                    hashcodeDataFile.setFileHashSha256(requestHashcodeDataFile.getFileHashSha256());
                    hashcodeDataFile.setFileHashSha512(requestHashcodeDataFile.getFileHashSha512());
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

        X509Certificate x509Certificate = Stream
                .of(SupportedCertificateEncoding.values())
                .filter(e -> e.isDecodable(signingCertificate))
                .map(e -> tryToCreateX509Certificate(e.decode(signingCertificate)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(c -> CertificateUtil.isCertificateActive(c) && CertificateUtil.isSigningCertificate(c))
                .findFirst()
                .orElseThrow(() -> new InvalidCertificateException("Invalid signing certificate"));

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

    static SignatureParameters transformSignatureParameters(String requestSignatureProfile, SignatureProductionPlace signatureProductionPlace, List<String> roles) {
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
        return MobileIdInformation.builder()
                .language(language)
                .messageToDisplay(messageToDisplay)
                .personIdentifier(personIdentifier)
                .phoneNo(phoneNo)
                .build();
    }

    static SmartIdInformation transformSmartIdInformation(String documentNumber, String country, String messageToDisplay, String personIdentifier) {
        return SmartIdInformation.builder()
                .country(country)
                .messageToDisplay(messageToDisplay)
                .personIdentifier(personIdentifier)
                .documentNumber(documentNumber)
                .build();
    }

    static GetContainerSignatureDetailsResponse transformSignatureToDetails(org.digidoc4j.Signature signature) {

        GetContainerSignatureDetailsResponse response = new GetContainerSignatureDetailsResponse();
        try {
            response.setClaimedSigningTime(signature.getClaimedSigningTime().toInstant().toString());
            response.setId(signature.getId());
            response.setSignerInfo(signature.getSigningCertificate().getSubjectName());
            if (signature.getOCSPCertificate() != null)
                response.setOcspCertificate(new String(Base64.getEncoder().encode(signature.getOCSPCertificate().getX509Certificate().getEncoded())));
            if (signature.getOCSPResponseCreationTime() != null)
                response.setOcspResponseCreationTime(signature.getOCSPResponseCreationTime().toInstant().toString());

            SignatureProductionPlace signatureProductionPlace = new SignatureProductionPlace();
            signatureProductionPlace.setCity(signature.getCity());
            signatureProductionPlace.setCountryName(signature.getCountryName());
            signatureProductionPlace.setPostalCode(signature.getPostalCode());
            signatureProductionPlace.setStateOrProvince(signature.getStateOrProvince());

            response.setSignatureProductionPlace(signatureProductionPlace);
            response.setSignatureProfile(signature.getProfile().name());
            response.setSigningCertificate(new String(Base64.getEncoder().encode(signature.getSigningCertificate().getX509Certificate().getEncoded())));
            if (signature.getTimeStampCreationTime() != null)
                response.setTimeStampCreationTime(signature.getTimeStampCreationTime().toInstant().toString());
            if (signature.getTimeStampTokenCertificate() != null)
                response.setTimeStampTokenCertificate(new String(Base64.getEncoder().encode(signature.getTimeStampTokenCertificate().getX509Certificate().getEncoded())));
            if (signature.getTrustedSigningTime() != null)
                response.setTrustedSigningTime(signature.getTrustedSigningTime().toInstant().toString());
            response.getRoles().addAll(signature.getSignerRoles());

        } catch (CertificateEncodingException e) {
            throw new InvalidSignatureException();
        }
        return response;
    }

    private static Optional<X509Certificate> tryToCreateX509Certificate(byte[] rawBytes) {
        try {
            return Optional.of(CertificateUtil.createX509Certificate(rawBytes));
        } catch (InvalidCertificateException e) {
            return Optional.empty();
        }
    }
}
