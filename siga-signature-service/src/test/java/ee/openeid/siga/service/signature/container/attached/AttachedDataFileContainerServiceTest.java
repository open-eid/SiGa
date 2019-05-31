package ee.openeid.siga.service.signature.container.attached;


import ee.openeid.siga.common.DataFile;
import ee.openeid.siga.common.Signature;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICE;
import static org.digidoc4j.Container.DocumentType.ASICE;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class AttachedDataFileContainerServiceTest {


    @InjectMocks
    AttachedDataFileContainerService containerService;

    @Mock
    private SessionService sessionService;

    @Before
    public void setUp() {
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder()
                .clientName("client1")
                .serviceName("Testimine")
                .serviceUuid("a7fd7728-a3ea-4975-bfab-f240a67e894f").build());

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    public void successfulCreateContainer() {
        List<DataFile> dataFiles = RequestUtil.createDataFileListWithOneFile();
        containerService.setSessionService(sessionService);

        String containerId = containerService.createContainer("test.asice", dataFiles);
        Assert.assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    public void successfulUploadContainer() throws Exception {
        String container = new String(Base64.getEncoder().encode(TestUtil.getFileInputStream(VALID_ASICE).readAllBytes()));
        String containerId = containerService.uploadContainer("test.asice", container);
        Assert.assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    public void successfulGetContainer() throws Exception {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAttachedDataFileSessionHolder());
        String base64container = containerService.getContainer(CONTAINER_ID);
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64container.getBytes()));
        Container container = ContainerBuilder.aContainer(ASICE).fromStream(inputStream).build();
        Assert.assertEquals(1, container.getSignatures().size());
    }

    @Test
    public void successfulGetSignatures() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAttachedDataFileSessionHolder());
        List<Signature> signatures = containerService.getSignatures(CONTAINER_ID);
        Assert.assertEquals("S0", signatures.get(0).getId());
        Assert.assertEquals("LT", signatures.get(0).getSignatureProfile());
        Assert.assertFalse(StringUtils.isBlank(signatures.get(0).getGeneratedSignatureId()));
        Assert.assertEquals("SERIALNUMBER=11404176865, GIVENNAME=MÄRÜ-LÖÖZ, SURNAME=ŽÕRINÜWŠKY, CN=\"ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865\", OU=digital signature, O=ESTEID, C=EE", signatures.get(0).getSignerInfo());
    }

    @Test
    public void successfulGetDataFiles() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAttachedDataFileSessionHolder());
        List<DataFile> dataFiles = containerService.getDataFiles(CONTAINER_ID);
        Assert.assertEquals("test.xml", dataFiles.get(0).getFileName());
        Assert.assertEquals("PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHByb2plY3QgeG1sbnM6amFjb2NvPSJhbnRsaWI6b3JnLmphY29jby5hbnQiIG5hbWU9ImRpZ2lkb2M0aiBidWlsZGVyIiBiYXNlZGlyPSIuLi8iIGRlZmF1bHQ9ImFsbCI+CiAgICA8cHJvcGVydHkgbmFtZT0iYnVpbGQuZGlyIiB2YWx1ZT0iYnVpbGQiLz4KICAgIDxwcm9wZXJ0eSBuYW1lPSJjb3ZlcmFnZS5kaXIiIHZhbHVlPSJjb3ZlcmFnZSIvPgogICAgPHByb3BlcnR5IG5hbWU9InZlcnNpb24iIHZhbHVlPSIwLjIiLz4KCiAgICA8dGFza2RlZiB1cmk9ImFudGxpYjpvcmcuamFjb2NvLmFudCIgcmVzb3VyY2U9Im9yZy9qYWNvY28vYW50L2FudGxpYi54bWwiPgogICAgICAgIDxjbGFzc3BhdGggcGF0aD0iLi4vbGliL2phY29jb2FudC5qYXIiLz4KICAgIDwvdGFza2RlZj4KCiAgICA8cGF0aCBpZD0iamF2YWMuY2xhc3NwYXRoIj4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1jb21tb24vdGFyZ2V0L2NsYXNzZXMiLz4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1kb2N1bWVudC90YXJnZXQvY2xhc3NlcyIvPgogICAgICAgIDxwYXRoZWxlbWVudCBsb2NhdGlvbj0ic2QtZHNzL2FwcHMvZHNzL2NvcmUvZHNzLXNlcnZpY2UvdGFyZ2V0L2NsYXNzZXMiLz4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1zcGkvdGFyZ2V0L2NsYXNzZXMiLz4KICAgICAgICA8ZmlsZXNldCBkaXI9Ii4uL2xpYiI+CiAgICAgICAgICAgIDxpbmNsdWRlIG5hbWU9IioqLyouamFyIi8+CiAgICAgICAgPC9maWxlc2V0PgogICAgPC9wYXRoPgoKICAgIDxwYXRoIGlkPSJydW4uY2xhc3NwYXRoIj4KICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249IiR7YnVpbGQuZGlyfSIvPgogICAgPC9wYXRoPgoKICAgIDx0YXJnZXQgbmFtZT0iamF2YWRvYyI+CiAgICAgICAgPGphdmFkb2MgcGFja2FnZW5hbWVzPSJvcmcuZGlnaWRvYzRqLmFwaSIgZGVzdGRpcj0iLi4vamF2YWRvYyI+CiAgICAgICAgICAgIDxzb3VyY2VwYXRoIHBhdGg9InNyYyIvPgogICAgICAgICAgICA8c291cmNlcGF0aCBwYXRoPSJzZC1kc3MvYXBwcy9kc3MvY29yZS9kc3MtY29tbW9uL3NyYyIvPgogICAgICAgICAgICA8c291cmNlcGF0aCBwYXRoPSJzZC1kc3MvYXBwcy9kc3MvY29yZS9kc3MtZG9jdW1lbnQvc3JjIi8+CiAgICAgICAgICAgIDxzb3VyY2VwYXRoIHBhdGg9InNkLWRzcy9hcHBzL2Rzcy9jb3JlL2Rzcy1zZXJ2aWNlL3NyYyIvPgogICAgICAgICAgICA8c291cmNlcGF0aCBwYXRoPSJzZC1kc3MvYXBwcy9kc3MvY29yZS9kc3Mtc3BpL3NyYyIvPgogICAgICAgIDwvamF2YWRvYz4KICAgICAgICA8amFyIGRlc3RmaWxlPSIke2J1aWxkLmRpcn0vZGlnaWRvYzRqLSR7dmVyc2lvbn0tamF2YWRvYy5qYXIiIGJhc2VkaXI9Ii4uL2phdmFkb2MiLz4KICAgIDwvdGFyZ2V0PgoKICAgIDx0YXJnZXQgbmFtZT0iY29tcGlsZSI+CiAgICAgICAgPGphdmFjIGRlc3RkaXI9IiR7YnVpbGQuZGlyfSIgaW5jbHVkZWFudHJ1bnRpbWU9ImZhbHNlIiBkZWJ1Zz0ib24iIHNvdXJjZT0iMS43Ij4KICAgICAgICAgICAgPHNyYyBwYXRoPSJzcmMiLz4KICAgICAgICAgICAgPHNyYyBwYXRoPSJ0ZXN0Ii8+CiAgICAgICAgICAgIDxjbGFzc3BhdGggcmVmaWQ9ImphdmFjLmNsYXNzcGF0aCIvPgogICAgICAgIDwvamF2YWM+CiAgICA8L3RhcmdldD4KCiAgICA8dGFyZ2V0IG5hbWU9InNvdXJjZSI+CiAgICAgICAgPGphciBkZXN0ZmlsZT0iJHtidWlsZC5kaXJ9L2RpZ2lkb2M0ai0ke3ZlcnNpb259LXNvdXJjZXMuamFyIiBiYXNlZGlyPSIuLi9zcmMiLz4KICAgIDwvdGFyZ2V0PgoKICAgIDx0YXJnZXQgbmFtZT0iYWxsIiBkZXBlbmRzPSJjbGVhbiwgamF2YWRvYywgc291cmNlLCBjb21waWxlLCB0ZXN0LCBjb3ZlcmFnZS5yZXBvcnQiLz4KCiAgICA8dGFyZ2V0IG5hbWU9InRlc3QiIGRlcGVuZHM9ImNvbXBpbGUiPgogICAgICAgIDxqYWNvY286Y292ZXJhZ2U+CiAgICAgICAgICAgIDxqdW5pdCBmb3JrPSJ0cnVlIj4KICAgICAgICAgICAgICAgIDxjbGFzc3BhdGggcmVmaWQ9ImphdmFjLmNsYXNzcGF0aCIvPgogICAgICAgICAgICAgICAgPGNsYXNzcGF0aD4KICAgICAgICAgICAgICAgICAgICA8cGF0aGVsZW1lbnQgbG9jYXRpb249ImJ1aWxkIi8+CiAgICAgICAgICAgICAgICA8L2NsYXNzcGF0aD4KICAgICAgICAgICAgICAgIDxiYXRjaHRlc3Q+CiAgICAgICAgICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIke2J1aWxkLmRpcn0iIGluY2x1ZGVzPSIqKi8qVGVzdCouY2xhc3MiLz4KICAgICAgICAgICAgICAgIDwvYmF0Y2h0ZXN0PgogICAgICAgICAgICAgICAgPGZvcm1hdHRlciB0eXBlPSJicmllZiIgdXNlZmlsZT0iZmFsc2UiLz4KICAgICAgICAgICAgICAgIDxmb3JtYXR0ZXIgdHlwZT0ieG1sIi8+CiAgICAgICAgICAgIDwvanVuaXQ+CiAgICAgICAgPC9qYWNvY286Y292ZXJhZ2U+CiAgICAgICAgPGp1bml0cmVwb3J0IHRvZGlyPSIke2J1aWxkLmRpcn0iPgogICAgICAgICAgICA8ZmlsZXNldCBkaXI9Ii4uLyI+CiAgICAgICAgICAgICAgICA8aW5jbHVkZSBuYW1lPSJURVNULSoueG1sIi8+CiAgICAgICAgICAgIDwvZmlsZXNldD4KICAgICAgICAgICAgPHJlcG9ydCBmb3JtYXQ9Im5vZnJhbWVzIiB0b2Rpcj0iJHtidWlsZC5kaXJ9L2h0bWwiLz4KICAgICAgICA8L2p1bml0cmVwb3J0PgogICAgPC90YXJnZXQ+CgogICAgPHRhcmdldCBuYW1lPSJjb3ZlcmFnZS5yZXBvcnQiPgogICAgICAgIDxqYWNvY286cmVwb3J0PgogICAgICAgICAgICA8ZXhlY3V0aW9uZGF0YT4KICAgICAgICAgICAgICAgIDxmaWxlIGZpbGU9ImphY29jby5leGVjIi8+CiAgICAgICAgICAgIDwvZXhlY3V0aW9uZGF0YT4KCiAgICAgICAgICAgIDxzdHJ1Y3R1cmUgbmFtZT0iRXhhbXBsZSBQcm9qZWN0Ij4KICAgICAgICAgICAgICAgIDxjbGFzc2ZpbGVzPgogICAgICAgICAgICAgICAgICAgIDxmaWxlc2V0IGRpcj0iJHtidWlsZC5kaXJ9Ij4KICAgICAgICAgICAgICAgICAgICAgICAgPGV4Y2x1ZGUgbmFtZT0iKiovKlRlc3QqIi8+CiAgICAgICAgICAgICAgICAgICAgICAgIDxleGNsdWRlIG5hbWU9InByb3RvdHlwZS8iLz4KICAgICAgICAgICAgICAgICAgICAgICAgPGV4Y2x1ZGUgbmFtZT0ib3JnL2RpZ2lkb2M0ai9tYWluLyIvPgogICAgICAgICAgICAgICAgICAgIDwvZmlsZXNldD4KICAgICAgICAgICAgICAgIDwvY2xhc3NmaWxlcz4KICAgICAgICAgICAgICAgIDxzb3VyY2VmaWxlcyBlbmNvZGluZz0iVVRGLTgiPgogICAgICAgICAgICAgICAgICAgIDxmaWxlc2V0IGRpcj0ic3JjIi8+CiAgICAgICAgICAgICAgICA8L3NvdXJjZWZpbGVzPgogICAgICAgICAgICA8L3N0cnVjdHVyZT4KCiAgICAgICAgICAgIDxodG1sIGRlc3RkaXI9IiR7Y292ZXJhZ2UuZGlyfSIvPgogICAgICAgIDwvamFjb2NvOnJlcG9ydD4KICAgIDwvdGFyZ2V0PgoKICAgIDx0YXJnZXQgbmFtZT0iY2xlYW4iPgogICAgICAgIDxkZWxldGUgaW5jbHVkZWVtcHR5ZGlycz0idHJ1ZSIgZmFpbG9uZXJyb3I9ImZhbHNlIj4KICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIuLi8iIGluY2x1ZGVzPSJURVNUKi54bWwiLz4KICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIke2J1aWxkLmRpcn0iLz4KICAgICAgICAgICAgPGZpbGVzZXQgZGlyPSIuLi9qYXZhZG9jIi8+CiAgICAgICAgPC9kZWxldGU+CiAgICAgICAgPGRlbGV0ZSBmaWxlPSJ0ZXN0U2F2ZVRvRmlsZS50eHQiLz4KICAgICAgICA8bWtkaXIgZGlyPSIuLi9qYXZhZG9jIi8+CiAgICA8L3RhcmdldD4KCjwvcHJvamVjdD4=", dataFiles.get(0).getContent());
    }

    @Test
    public void successfulCloseSession() {
        String result = containerService.closeSession(CONTAINER_ID);
        Assert.assertEquals(SessionResult.OK.name(), result);
    }
}
