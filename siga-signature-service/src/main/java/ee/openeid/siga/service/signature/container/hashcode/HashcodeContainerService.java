package ee.openeid.siga.service.signature.container.hashcode;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.DuplicateDataFileException;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.Signature;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.service.signature.session.HashcodeSessionHolder;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.model.MimeType;
import lombok.RequiredArgsConstructor;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HashcodeContainerService implements HashcodeSessionHolder {
    private final SessionService sessionService;
    private final Configuration configuration;

    public String createContainer(List<HashcodeDataFile> dataFiles) {

        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        dataFiles.forEach(dataFile -> {
            updateMimeTypeIfNotSet(dataFile);
            hashcodeContainer.addDataFile(dataFile);
        });

        String containerId = generateContainerId();
        sessionService.update(transformContainerToSession(containerId, hashcodeContainer));
        return containerId;
    }

    public String uploadContainer(String container) {
        String containerId = generateContainerId();
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        HashcodeContainer hashcodeContainer = new HashcodeContainer(sigaUserDetails.getServiceType());
        hashcodeContainer.open(Base64.getDecoder().decode(container.getBytes()));
        sessionService.update(transformContainerToSession(containerId, hashcodeContainer));
        return containerId;
    }

    public String getContainer(String containerId) {
        HashcodeContainerSession sessionHolder = getSessionHolder(containerId);

        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        sessionHolder.getSignatures().forEach(signatureWrapper -> hashcodeContainer.getSignatures().add(signatureWrapper));
        sessionHolder.getDataFiles().forEach(dataFile -> hashcodeContainer.getDataFiles().add(dataFile));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        hashcodeContainer.save(outputStream);
        byte[] container = outputStream.toByteArray();

        return new String(Base64.getEncoder().encode(container));
    }


    public Result closeSession(String containerId) {
        sessionService.remove(containerId);
        return Result.OK;
    }

    public List<Signature> getSignatures(String containerId) {
        HashcodeContainerSession sessionHolder = getSessionHolder(containerId);
        List<Signature> signatures = new ArrayList<>();
        sessionHolder.getSignatures().forEach(signatureWrapper -> signatures.add(transformSignature(signatureWrapper)));
        return signatures;
    }

    public org.digidoc4j.Signature getSignature(String containerId, String signatureId) {
        HashcodeContainerSession sessionHolder = getSessionHolder(containerId);
        Optional<HashcodeSignatureWrapper> signatureWrapper = sessionHolder.getSignatures().stream()
                .filter(wrapper -> wrapper.getGeneratedSignatureId().equals(signatureId))
                .findAny();
        if (signatureWrapper.isEmpty()) {
            throw new ResourceNotFoundException("Signature with id  " + signatureId + " not found");
        }
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(configuration);
        return builder.openAdESSignature(signatureWrapper.get().getSignature());
    }

    public List<HashcodeDataFile> getDataFiles(String containerId) {
        HashcodeContainerSession sessionHolder = getSessionHolder(containerId);
        return sessionHolder.getDataFiles();
    }

    public Result addDataFiles(String containerId, List<HashcodeDataFile> dataFiles) {
        HashcodeContainerSession sessionHolder = getSessionHolder(containerId);
        validateIfSessionMutable(sessionHolder);
        dataFiles.forEach(dataFile -> {
            validateNotDuplicateFile(dataFile, sessionHolder);
            updateMimeTypeIfNotSet(dataFile);
        });
        sessionHolder.getDataFiles().addAll(dataFiles);
        sessionService.update(sessionHolder);
        return Result.OK;
    }

    public Result removeDataFile(String containerId, String datafileName) {
        HashcodeContainerSession sessionHolder = getSessionHolder(containerId);
        validateIfSessionMutable(sessionHolder);
        if (sessionHolder.getDataFiles().stream().noneMatch(dataFile -> dataFile.getFileName().equals(datafileName))) {
            throw new ResourceNotFoundException("Data file named " + datafileName + " not found");
        }
        sessionHolder.getDataFiles().removeIf(dataFile -> dataFile.getFileName().equals(datafileName));
        sessionService.update(sessionHolder);
        return Result.OK;
    }

    public Signature transformSignature(HashcodeSignatureWrapper signatureWrapper) {
        Signature signature = new Signature();
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(configuration);
        org.digidoc4j.Signature dd4jSignature;
        try {
            dd4jSignature = builder.openAdESSignature(signatureWrapper.getSignature());
        } catch (DigiDoc4JException e) {
            throw new InvalidSignatureException(e.getMessage());
        } catch (IllegalArgumentException e) {
            // TODO: Since Spring Boot was upgraded to version 2.3.X, parsing an invalid signature began to throw
            //  an IllegalArgumentException through DSS and from org.apache.commons.codec.binary.Base64.validateCharacter:
            //  "Last encoded character (before the paddings if any) is a valid base 64 alphabet but not a possible value.
            //  Expected the discarded bits to be zero."
            //  This might need a revisit after DD4J/DSS has been updated.
            throw new InvalidSignatureException("Failed to parse detached XAdES signature: " + e.getMessage());
        }
        signature.setId(dd4jSignature.getId());
        signature.setGeneratedSignatureId(signatureWrapper.getGeneratedSignatureId());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        return signature;
    }

    private void validateIfSessionMutable(HashcodeContainerSession session) {
        if (!session.getSignatures().isEmpty()) {
            throw new InvalidSessionDataException("Unable to add/remove data file. Container contains signature(s)");
        }
    }

    private HashcodeContainerSession transformContainerToSession(String containerId, HashcodeContainer container) {
        String sessionId = sessionService.getSessionId(containerId);
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .dataFiles(container.getDataFiles())
                .signatures(container.getSignatures())
                .build();
    }

    private void validateNotDuplicateFile(HashcodeDataFile dataFileToAdd, HashcodeContainerSession sessionHolder) {
        sessionHolder.getDataFiles().stream()
                .filter(dataFile -> dataFile.getFileName().equals(dataFileToAdd.getFileName()))
                .findFirst()
                .ifPresent(dataFile -> {
                    throw new DuplicateDataFileException("Duplicate data files not allowed: " + dataFile.getFileName());
                });
    }

    private static void updateMimeTypeIfNotSet(HashcodeDataFile dataFile) {
        if (dataFile.getMimeType() == null) {
            MimeType mimeType = MimeType.fromFileName(dataFile.getFileName());
            dataFile.setMimeType(mimeType.getMimeTypeString());
        }
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    String generateContainerId() {
        return UUIDGenerator.generateUUID();
    }
}
