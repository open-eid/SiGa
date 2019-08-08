package ee.openeid.siga.auth.filter;

import ee.openeid.siga.auth.model.SigaConnection;
import ee.openeid.siga.auth.model.SigaService;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.auth.repository.ServiceRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RequestDataVolumeFilterTest {

    @InjectMocks
    private RequestDataVolumeFilter filter;
    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private ServiceRepository serviceRepository;

    private static final String TEST_URI = "/hashcodecontainers";
    private static final int SERVICE_ID = 1;
    private static final String DEFAULT_HTTP_METHOD = "POST";
    private static final String CONTAINER_ID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @Before
    public void setUp() throws Exception {
        request = new MockHttpServletRequest();
        request.setMethod(DEFAULT_HTTP_METHOD);
        request.setRequestURI(TEST_URI);
        request.setContent(mockCreateContainer().toString().getBytes());
        response = new MockHttpServletResponse();
        filterChain = (request, response) -> {
            mockResponse(response);
            response.getOutputStream();
        };
        filter.setConnectionRepository(connectionRepository);
        filter.setServiceRepository(serviceRepository);
    }

    @Test
    public void getHttpMethod() throws ServletException, IOException {
        request.setMethod("GET");
        filter.doFilter(request, response, filterChain);
        verify(serviceRepository, never()).findByUuid(any());
    }

    @Test
    public void noServiceFound() throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);
        verify(serviceRepository).findByUuid(any());
        verify(connectionRepository, never()).findAllByServiceId(SERVICE_ID);
    }

    @Test
    public void serviceFoundNewContainer() throws ServletException, IOException {
        when(serviceRepository.findByUuid(any())).thenReturn(mockSigaService());
        filter.doFilter(request, response, filterChain);
        verify(connectionRepository).findAllByServiceId(SERVICE_ID);
        verify(connectionRepository, never()).findAllByContainerId(any());
        verify(connectionRepository).saveAndFlush(any(SigaConnection.class));
    }

    @Test
    public void serviceFoundExistingContainer() throws ServletException, IOException {
        when(serviceRepository.findByUuid(any())).thenReturn(mockSigaService());
        request.setRequestURI(TEST_URI + "/" + CONTAINER_ID + "/remotesigning");
        when(connectionRepository.findAllByContainerId(CONTAINER_ID)).thenReturn(mockSigaConnection());
        filter.doFilter(request, response, filterChain);
        verify(connectionRepository).saveAndFlush(any(SigaConnection.class));
    }

    @Test
    public void serviceFoundExistingContainerAndConnectionExpired() throws ServletException, IOException {
        when(serviceRepository.findByUuid(any())).thenReturn(mockSigaService());
        request.setRequestURI(TEST_URI + "/" + CONTAINER_ID + "/remotesigning");
        filter.doFilter(request, response, filterChain);
        verify(connectionRepository).findAllByContainerId(CONTAINER_ID);
        verify(connectionRepository, never()).saveAndFlush(any(SigaConnection.class));
    }

    @Test
    public void connectionCountLimitless() throws ServletException, IOException {
        Optional<SigaService> sigaService = mockSigaService();
        sigaService.get().setMaxConnectionCount(-1);
        when(serviceRepository.findByUuid(any())).thenReturn(sigaService);
        filter.doFilter(request, response, filterChain);
        verify(connectionRepository).findAllByServiceId(SERVICE_ID);
        verify(connectionRepository, never()).findAllByContainerId(any());
        verify(connectionRepository).saveAndFlush(any(SigaConnection.class));
    }

    @Test
    public void connectionsSizeLimitless() throws ServletException, IOException {
        Optional<SigaService> sigaService = mockSigaService();
        sigaService.get().setMaxConnectionsSize(-1);
        when(serviceRepository.findByUuid(any())).thenReturn(sigaService);
        filter.doFilter(request, response, filterChain);
        verify(connectionRepository).findAllByServiceId(SERVICE_ID);
        verify(connectionRepository, never()).findAllByContainerId(any());
        verify(connectionRepository).saveAndFlush(any(SigaConnection.class));
    }

    @Test
    public void connectionSizeLimitless() throws ServletException, IOException {
        Optional<SigaService> sigaService = mockSigaService();
        sigaService.get().setMaxConnectionSize(-1);
        when(serviceRepository.findByUuid(any())).thenReturn(sigaService);
        filter.doFilter(request, response, filterChain);
        verify(connectionRepository).findAllByServiceId(SERVICE_ID);
        verify(connectionRepository, never()).findAllByContainerId(any());
        verify(connectionRepository).saveAndFlush(any(SigaConnection.class));
    }

    @Test
    public void connectionCountExceeded() throws ServletException, IOException {
        Optional<SigaService> sigaService = mockSigaService();
        sigaService.get().setMaxConnectionCount(0);
        when(serviceRepository.findByUuid(any())).thenReturn(sigaService);
        filterChain = new MockFilterChain();
        filter.doFilter(request, response, filterChain);
        Assert.assertEquals("{\"errorCode\":\"CONNECTION_LIMIT_EXCEPTION\",\"errorMessage\":\"Number of max connections exceeded\"}", response.getContentAsString());
    }

    @Test
    public void connectionsSizeExceeded() throws ServletException, IOException {
        Optional<SigaService> sigaService = mockSigaService();
        sigaService.get().setMaxConnectionsSize(0);
        when(serviceRepository.findByUuid(any())).thenReturn(sigaService);
        filterChain = new MockFilterChain();
        filter.doFilter(request, response, filterChain);
        Assert.assertEquals("{\"errorCode\":\"CONNECTION_LIMIT_EXCEPTION\",\"errorMessage\":\"Size of total connections exceeded\"}", response.getContentAsString());
    }

    @Test
    public void connectionSizeExceeded() throws ServletException, IOException {
        Optional<SigaService> sigaService = mockSigaService();
        sigaService.get().setMaxConnectionSize(0);
        when(serviceRepository.findByUuid(any())).thenReturn(sigaService);
        filterChain = new MockFilterChain();
        filter.doFilter(request, response, filterChain);
        Assert.assertEquals("{\"errorCode\":\"CONNECTION_LIMIT_EXCEPTION\",\"errorMessage\":\"Size of connection exceeded\"}", response.getContentAsString());

    }

    private Optional<SigaService> mockSigaService() {
        SigaService sigaService = new SigaService();
        sigaService.setName("service");
        sigaService.setId(SERVICE_ID);
        sigaService.setMaxConnectionCount(2);
        sigaService.setMaxConnectionsSize(500);
        sigaService.setMaxConnectionSize(2);
        return Optional.of(sigaService);
    }

    private Optional<SigaConnection> mockSigaConnection() {
        SigaConnection sigaConnection = new SigaConnection();
        sigaConnection.setContainerId(CONTAINER_ID);
        sigaConnection.setService(mockSigaService().get());
        sigaConnection.setSize(500);
        return Optional.of(sigaConnection);
    }

    private JSONObject mockCreateContainer() throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        dataFile.put("fileName", "test.txt");
        dataFile.put("fileHashSha256", "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=");
        dataFile.put("fileHashSha512", "vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg==");
        dataFile.put("fileSize", 10);
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);

        return request;
    }

    private void mockResponse(ServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("containerId", CONTAINER_ID);
        byte[] bytes = jsonResponse.toString().getBytes();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
        baos.write(bytes, 0, bytes.length);

        try (OutputStream out = response.getOutputStream()) {
            baos.writeTo(out);
            out.flush();
        }
    }
}
