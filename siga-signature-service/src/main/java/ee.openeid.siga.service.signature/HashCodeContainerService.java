package ee.openeid.siga.service.signature;

import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.session.HashCodeContainerSessionHolder;
import ee.openeid.siga.service.signature.hashcode.HashCodeContainer;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.HashCodeSessionService;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.*;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class HashCodeContainerService extends ContainerService {

    private HashCodeSessionService sessionService;

    public CreateHashCodeContainerResponse createContainer(CreateHashCodeContainerRequest request) {

        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        request.getDataFiles().forEach(dataFile ->
                hashCodeContainer.addDataFile(ContainerUtil.transformDataFileToHashCodeDataFile(dataFile))
        );
        OutputStream outputStream = new ByteArrayOutputStream();
        hashCodeContainer.save(outputStream);

        String sessionId = SessionIdGenerator.generateSessionId();
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer, request.getContainerName()));
        CreateHashCodeContainerResponse response = new CreateHashCodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    public UploadHashCodeContainerResponse uploadContainer(UploadHashCodeContainerRequest request) {
        String sessionId = SessionIdGenerator.generateSessionId();
        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(request.getContainer().getBytes()));
        hashCodeContainer.open(inputStream);
        sessionService.update(sessionId, transformContainerToSession(hashCodeContainer, request.getContainerName()));

        UploadHashCodeContainerResponse response = new UploadHashCodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    public GetHashCodeContainerResponse getContainer(String containerId) {
        HashCodeContainerSessionHolder sessionHolder = getSession(containerId);

        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        sessionHolder.getSignatures().forEach(signatureWrapper -> hashCodeContainer.getSignatures().add(signatureWrapper));
        sessionHolder.getDataFiles().forEach(dataFile -> hashCodeContainer.getDataFiles().add(dataFile));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        hashCodeContainer.save(outputStream);
        byte[] container = outputStream.toByteArray();

        GetHashCodeContainerResponse response = new GetHashCodeContainerResponse();
        response.setContainer(new String(Base64.getEncoder().encode(container)));
        response.setContainerName(sessionHolder.getContainerName());
        return response;
    }

    public GetHashCodeSignaturesResponse getSignatures(String containerId) {
        HashCodeContainerSessionHolder sessionHolder = getSession(containerId);
        List<Signature> signatures = new ArrayList<>();
        sessionHolder.getSignatures().forEach(signatureWrapper -> {
            signatures.add(transformSignature(signatureWrapper));
        });
        GetHashCodeSignaturesResponse response = new GetHashCodeSignaturesResponse();
        response.getSignatures().addAll(signatures);
        return response;

    }

    private Signature transformSignature(SignatureWrapper signatureWrapper) {
        Signature signature = new Signature();
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(new Configuration());
        org.digidoc4j.Signature dd4jSignature = builder.openAdESSignature(signatureWrapper.getSignature());
        signature.setId(dd4jSignature.getId());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        return signature;
    }

    private HashCodeContainerSessionHolder transformContainerToSession(HashCodeContainer container, String containerName) {
        return HashCodeContainerSessionHolder.builder()
                .containerName(containerName)
                .dataFiles(container.getDataFiles())
                .signatures(container.getSignatures())
                .build();
    }

    @Autowired
    protected void setSessionService(HashCodeSessionService sessionService) {
        this.sessionService = sessionService;
    }


    @Override
    SessionService getSessionService() {
        return sessionService;
    }
}
