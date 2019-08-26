package ee.openeid.siga.mobileid.client;

import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.MidException;
import ee.openeid.siga.common.exception.SigaApiException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.ws.soap.client.SoapFaultClientException;

import java.util.Optional;

public class SoapFaultHandlingUtilTest {

    @Test
    public void soapFault300() {
        SigaApiException result = SoapFaultHandlingUtil.handleSoapFaultClientException(mockSoapFaultClientException("300"));
        Assert.assertTrue(result instanceof MidException);
        Assert.assertEquals("GENERAL_ERROR", result.getMessage());
    }

    @Test
    public void soapFault301() {
        SigaApiException result = SoapFaultHandlingUtil.handleSoapFaultClientException(mockSoapFaultClientException("301"));
        Assert.assertTrue(result instanceof MidException);
        Assert.assertEquals("NOT_FOUND", result.getMessage());
    }

    @Test
    public void soapFault302() {
        SigaApiException result = SoapFaultHandlingUtil.handleSoapFaultClientException(mockSoapFaultClientException("302"));
        Assert.assertTrue(result instanceof MidException);
        Assert.assertEquals("NOT_ACTIVE", result.getMessage());
    }

    @Test
    public void soapFault303() {
        SigaApiException result = SoapFaultHandlingUtil.handleSoapFaultClientException(mockSoapFaultClientException("303"));
        Assert.assertTrue(result instanceof MidException);
        Assert.assertEquals("NOT_ACTIVE", result.getMessage());
    }

    @Test
    public void soapFault304() {
        SigaApiException result = SoapFaultHandlingUtil.handleSoapFaultClientException(mockSoapFaultClientException("304"));
        Assert.assertTrue(result instanceof MidException);
        Assert.assertEquals("NOT_ACTIVE", result.getMessage());
    }

    @Test
    public void soapFault305() {
        SigaApiException result = SoapFaultHandlingUtil.handleSoapFaultClientException(mockSoapFaultClientException("305"));
        Assert.assertTrue(result instanceof MidException);
        Assert.assertEquals("NOT_ACTIVE", result.getMessage());
    }

    @Test
    public void unhandledSoapFault() {
        SigaApiException result = SoapFaultHandlingUtil.handleSoapFaultClientException(mockSoapFaultClientException("200"));
        Assert.assertTrue(result instanceof ClientException);
        Assert.assertEquals("Mobile-ID service error", result.getMessage());
    }

    private static SoapFaultClientException mockSoapFaultClientException(String fault) {
        SoapFaultClientException exception = Mockito.mock(SoapFaultClientException.class);
        Mockito.doReturn(fault).when(exception).getFaultStringOrReason();
        return exception;
    }

}