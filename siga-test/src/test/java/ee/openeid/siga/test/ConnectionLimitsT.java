package ee.openeid.siga.test;

import ee.openeid.siga.common.Result;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConnectionLimitsT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient2Service5();
    }

    @Test
    public void connectionLimitReached() throws Exception {
        String sessionArray[] = new String[6];
        Boolean connectionLimitReached = false;
        for(int i=1; i<=5; i++) {
            Response validResponse = postCreateContainer(flow, asicContainersDataRequestWithDefault());
            sessionArray[i] = flow.getContainerId();
            if (validResponse.getStatusCode() != 200) {
                connectionLimitReached = true;
            }
        }

        Response errorResponse = postCreateContainer(flow, asicContainersDataRequestWithDefault());

        for(int i=1; i<=5; i++) {
            flow.setContainerId(sessionArray[i]);
            deleteContainer(flow);
        }

        assertThat("Max connection limit reached before configured value", connectionLimitReached, equalTo(false));
        expectError(errorResponse, 400, CONNECTION_LIMIT_EXCEPTION);
    }

    @Test
    public void connectionSizeReached() throws Exception {
        Response errorResponse = postUploadContainer(flow, asicContainerRequestFromFile("2379KB_file.asice"));

        expectError(errorResponse, 400, CONNECTION_LIMIT_EXCEPTION);
    }

    @Test
    public void connectionSizeReachedInTotal() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("1385KB_file.asice"));
        Response errorResponse = addDataFile(flow, addDataFileToAsicRequestFromFile("1391KB_picture.JPG"));
        deleteContainer(flow);
        expectError(errorResponse, 400, CONNECTION_LIMIT_EXCEPTION);
    }

    @Test
    public void connectionsTotalSizeReached() throws Exception {
        String sessionArray[] = new String[6];
        for(int i=1; i<=2; i++) {
            Response validResponse = postUploadContainer(flow, asicContainerRequestFromFile("1385KB_file.asice"));
            sessionArray[i] = flow.getContainerId();
            validResponse.then()
                    .statusCode(200);
        }

        Response errorResponse = postUploadContainer(flow, asicContainerRequestFromFile("1385KB_file.asice"));

        for(int i=1; i<=2; i++) {
            flow.setContainerId(sessionArray[i]);
            deleteContainer(flow);
        }

        expectError(errorResponse, 400, CONNECTION_LIMIT_EXCEPTION);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
