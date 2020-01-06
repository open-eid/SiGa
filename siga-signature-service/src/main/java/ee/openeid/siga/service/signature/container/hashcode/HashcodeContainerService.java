package ee.openeid.siga.service.signature.container.hashcode;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.Signature;
import ee.openeid.siga.common.session.HashcodeContainerSessionHolder;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.service.signature.session.HashcodeSessionHolder;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.model.MimeType;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class HashcodeContainerService implements HashcodeSessionHolder {

    private SessionService sessionService;
    private Configuration configuration;

    public String createContainer(List<HashcodeDataFile> dataFiles) {

        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        dataFiles.forEach(dataFile -> {
            updateMimeTypeIfNotSet(dataFile);
            hashcodeContainer.addDataFile(dataFile);
        });

        String sessionId = UUIDGenerator.generateUUID();
        sessionService.update(sessionId, transformContainerToSession(sessionId, hashcodeContainer));
        return sessionId;
    }

    public String uploadContainer(String container) {
        String sessionId = UUIDGenerator.generateUUID();
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(container.getBytes()));
        hashcodeContainer.open(inputStream);
        sessionService.update(sessionId, transformContainerToSession(sessionId, hashcodeContainer));
        return sessionId;
    }

    public String getContainer(String containerId) {
        HashcodeContainerSessionHolder sessionHolder = getSessionHolder(containerId);

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
        HashcodeContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        List<Signature> signatures = new ArrayList<>();
        sessionHolder.getSignatures().forEach(signatureWrapper -> signatures.add(transformSignature(signatureWrapper)));
        return signatures;
    }

    public org.digidoc4j.Signature getSignature(String containerId, String signatureId) {
        HashcodeContainerSessionHolder sessionHolder = getSessionHolder(containerId);
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
        HashcodeContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        return sessionHolder.getDataFiles();
    }

    public Result addDataFiles(String containerId, List<HashcodeDataFile> dataFiles) {
        HashcodeContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        validateIfSessionMutable(sessionHolder);
        dataFiles.forEach(HashcodeContainerService::updateMimeTypeIfNotSet);
        sessionHolder.getDataFiles().addAll(dataFiles);
        sessionService.update(containerId, sessionHolder);
        return Result.OK;
    }

    public Result removeDataFile(String containerId, String datafileName) {
        HashcodeContainerSessionHolder sessionHolder = getSessionHolder(containerId);
        validateIfSessionMutable(sessionHolder);
        if (sessionHolder.getDataFiles().stream().noneMatch(dataFile -> dataFile.getFileName().equals(datafileName))) {
            throw new ResourceNotFoundException("Data file named " + datafileName + " not found");
        }
        sessionHolder.getDataFiles().removeIf(dataFile -> dataFile.getFileName().equals(datafileName));
        sessionService.update(containerId, sessionHolder);
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
        }
        signature.setId(dd4jSignature.getId());
        signature.setGeneratedSignatureId(signatureWrapper.getGeneratedSignatureId());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        return signature;
    }

    private void validateIfSessionMutable(HashcodeContainerSessionHolder session) {
        if (!session.getSignatures().isEmpty()) {
            throw new InvalidSessionDataException("Unable to add/remove data file. Container contains signature(s)");
        }
    }

    private HashcodeContainerSessionHolder transformContainerToSession(String sessionId, HashcodeContainer container) {
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return HashcodeContainerSessionHolder.builder()
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .dataFiles(container.getDataFiles())
                .signatures(container.getSignatures())
                .build();
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

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

}
