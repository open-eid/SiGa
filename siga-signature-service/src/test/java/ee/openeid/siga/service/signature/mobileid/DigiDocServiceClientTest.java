package ee.openeid.siga.service.signature.mobileid;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
import ee.openeid.siga.mobileid.model.mid.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;

@RunWith(MockitoJUnitRunner.class)
public class DigiDocServiceClientTest {

    private static final String DEFAULT_MOCK_SESSION_CODE = "session-code";

    @Mock
    private DigiDocService digiDocService;
    @Mock
    private MobileIdService mobileIdService;
    @InjectMocks
    private DigiDocServiceClient digiDocServiceClient;


    @Test
    public void getStatus_ddsReturnsOutstandingTransaction() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.OUTSTANDING_TRANSACTION));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.OUTSTANDING_TRANSACTION, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsSignature() {
        GetMobileSignHashStatusResponse statusResponse = createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.SIGNATURE);
        statusResponse.setSignature("signature-bytes".getBytes(StandardCharsets.UTF_8));
        stubGetMobileSignHashStatusResponse(statusResponse);

        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.SIGNATURE, response.getStatus());
        Assert.assertSame(statusResponse.getSignature(), response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsExpiredTransaction() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.EXPIRED_TRANSACTION));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.EXPIRED_TRANSACTION, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsUserCancel() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.USER_CANCEL));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.USER_CANCEL, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsMidNotReady() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.MID_NOT_READY));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.MID_NOT_READY, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsInternalError() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.INTERNAL_ERROR));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.INTERNAL_ERROR, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsRevokedCertificate() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.REVOKED_CERTIFICATE));
        try {
            digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        } catch (ClientException e) {
            Assert.assertEquals("Mobile-ID service returned unexpected response", e.getMessage());
        }
    }

    @Test
    public void getStatus_ddsReturnsNotValid() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.NOT_VALID));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.NOT_VALID, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsSendingError() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.SENDING_ERROR));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.SENDING_ERROR, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsSimError() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.SIM_ERROR));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.SIM_ERROR, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsPhoneAbsent() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.PHONE_ABSENT));
        GetStatusResponse response = digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        Assert.assertEquals(MidStatus.PHONE_ABSENT, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ddsReturnsUserAuthenticated() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.USER_AUTHENTICATED));
        try {
            digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        } catch (ClientException e) {
            Assert.assertEquals("Mobile-ID service returned unexpected response", e.getMessage());
        }
    }

    @Test
    public void getStatus_ddsReturnsOcspUnauthorized() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.OCSP_UNAUTHORIZED));
        try {
            digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        } catch (ClientException e) {
            Assert.assertEquals("Mobile-ID service returned unexpected response", e.getMessage());
        }
    }

    @Test
    public void getStatus_ddsReturnsPhoneTimeout() {
        stubGetMobileSignHashStatusResponse(createDefaultGetMobileSignHashStatusResponse(ProcessStatusType.PHONE_TIMEOUT));
        try {
            digiDocServiceClient.getStatus(DEFAULT_MOCK_SESSION_CODE, createDefaultMobileIdInformation());
        } catch (ClientException e) {
            Assert.assertEquals("Mobile-ID service returned unexpected response", e.getMessage());
        }
    }


    private MobileIdInformation createDefaultMobileIdInformation() {
        return MobileIdInformation.builder()
                .relyingPartyName("relying-party-name")
                .build();
    }

    private GetMobileSignHashStatusResponse createDefaultGetMobileSignHashStatusResponse(ProcessStatusType status) {
        GetMobileSignHashStatusResponse getMobileSignHashStatusResponse = new GetMobileSignHashStatusResponse();
        getMobileSignHashStatusResponse.setSesscode(DEFAULT_MOCK_SESSION_CODE);
        getMobileSignHashStatusResponse.setStatus(status);
        return getMobileSignHashStatusResponse;
    }

    private void stubGetMobileSignHashStatusResponse(GetMobileSignHashStatusResponse response) {
        Mockito.doReturn(response).when(mobileIdService).getMobileSignHashStatus(Mockito.anyString());
    }

}