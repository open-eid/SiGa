package ee.openeid.siga.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.HttpServletFilterResponseWrapper;
import ee.openeid.siga.auth.model.SigaConnection;
import ee.openeid.siga.auth.model.SigaService;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.auth.repository.ServiceRepository;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.webapp.json.ErrorResponse;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.X_AUTHORIZATION_SERVICE_UUID;

@Component
public class RequestDataVolumeFilter extends OncePerRequestFilter {

    private ServiceRepository serviceRepository;
    private ConnectionRepository connectionRepository;
    private final static long LIMITLESS = -1;
    private final static String OBSERVABLE_HTTP_METHOD = "POST";
    private final static String ASIC_CONTAINERS_ENDPOINT = "/containers/";
    private final static String HASHCODE_CONTAINERS_ENDPOINT = "/hashcodecontainers/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (OBSERVABLE_HTTP_METHOD.equals(request.getMethod())) {
            long requestSize = request.getContentLengthLong();
            Optional<SigaService> user = serviceRepository.findByUuid(request.getHeader(X_AUTHORIZATION_SERVICE_UUID.getValue()));
            if (user.isPresent()) {
                HttpServletFilterResponseWrapper wrapperResponse = new HttpServletFilterResponseWrapper(response);
                SigaService sigaService = user.get();
                String requestUrl = request.getRequestURI();

                Optional<List<SigaConnection>> optionalSigaConnections = connectionRepository.findAllByServiceId(sigaService.getId());
                List<SigaConnection> connections = optionalSigaConnections.orElseGet(ArrayList::new);
                validate(wrapperResponse, sigaService, connections, requestSize);

                filterChain.doFilter(request, wrapperResponse);

                refreshConnectionData(sigaService, requestSize, wrapperResponse, requestUrl);
                return;
            }
        }
        filterChain.doFilter(request, response);

    }

    private void throwError(HttpServletFilterResponseWrapper response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try (OutputStream out = response.getOutputStream()) {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setErrorCode(ErrorResponseCode.CONNECTION_LIMIT_EXCEPTION.name());
            errorResponse.setErrorMessage(message);
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(out, errorResponse);
            out.flush();
        }
    }

    private String getContainerIdFromUrl(String url) {
        String urlPrefix;
        if (url.contains(ASIC_CONTAINERS_ENDPOINT)) {
            urlPrefix = ASIC_CONTAINERS_ENDPOINT;
        } else if (url.contains(HASHCODE_CONTAINERS_ENDPOINT)) {
            urlPrefix = HASHCODE_CONTAINERS_ENDPOINT;
        } else {
            return null;
        }
        String suffix = url.substring(url.indexOf(urlPrefix) + urlPrefix.length());
        return suffix.substring(0, suffix.indexOf("/"));
    }

    private void validate(HttpServletFilterResponseWrapper wrapperResponse, SigaService sigaService, List<SigaConnection> connections, long requestLength) throws IOException {
        if (!validateConnectionCount(wrapperResponse, sigaService, connections.size()))
            return;
        long existingSize = calculateSize(connections);
        validationConnectionsSize(wrapperResponse, sigaService, existingSize, requestLength);
    }

    private String getContainerIdFromResponse(HttpServletFilterResponseWrapper wrapperResponse) {
        String content = wrapperResponse.getContent();
        if (content == null || !content.contains("containerId"))
            return null;
        JSONObject jsonObject = new JSONObject(content);
        return jsonObject.getString("containerId");
    }

    private long calculateSize(List<SigaConnection> connections) {
        long size = 0;
        for (SigaConnection connection : connections) {
            size += connection.getSize();
        }
        return size;
    }

    private boolean isNewContainerUrl(String url) {
        return url.endsWith("/upload/containers") || url.endsWith("/upload/containers") || url.endsWith("/containers") || url.endsWith("/hashcodecontainers");
    }

    private void refreshConnectionData(SigaService sigaService, long requestSize, HttpServletFilterResponseWrapper response, String requestUrl) {
        String containerId;
        boolean isNewContainer = isNewContainerUrl(requestUrl);
        if (isNewContainer) {
            containerId = getContainerIdFromResponse(response);
            if (containerId != null)
                insertConnectionData(requestSize, containerId, sigaService);
        } else {
            containerId = getContainerIdFromUrl(requestUrl);
            Optional<SigaConnection> connectionOptional = connectionRepository.findAllByContainerId(containerId);
            if (connectionOptional.isPresent()) {
                SigaConnection connection = connectionOptional.get();
                connection.setSize(connection.getSize() + requestSize);
                connectionRepository.saveAndFlush(connection);
            }
        }
    }

    private void insertConnectionData(long requestSize, String containerId, SigaService sigaService) {
        SigaConnection sigaConnection = new SigaConnection();
        sigaConnection.setService(sigaService);
        sigaConnection.setContainerId(containerId);
        sigaConnection.setSize(requestSize);
        connectionRepository.saveAndFlush(sigaConnection);
    }

    private void validationConnectionsSize(HttpServletFilterResponseWrapper wrapperResponse, SigaService sigaService, double currentSize, double newSize) throws IOException {
        if (sigaService.getMaxConnectionsSize() == LIMITLESS)
            return;
        double currentSizeInMb = (currentSize + newSize) / 1024 / 1024;
        if (currentSizeInMb >= sigaService.getMaxConnectionsSize()) {
            throwError(wrapperResponse, "Size of total connections exceeded");
        }
    }

    private boolean validateConnectionCount(HttpServletFilterResponseWrapper wrapperResponse, SigaService sigaService, int currentCount) throws IOException {
        if (sigaService.getMaxConnectionCount() == LIMITLESS)
            return true;
        if (currentCount + 1 >= sigaService.getMaxConnectionCount()) {
            throwError(wrapperResponse, "Number of max connections exceeded");
            return false;
        }
        return true;
    }

    @Autowired
    public void setServiceRepository(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Autowired
    public void setConnectionRepository(ConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }


}
