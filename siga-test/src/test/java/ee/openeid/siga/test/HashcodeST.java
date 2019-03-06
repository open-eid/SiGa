package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;

import ee.openeid.siga.test.utils.RequestBuilder;


import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainer;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class HashcodeST extends TestBase{

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = new SigaApiFlow();
    }

    @Test
    public void createHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response status = postCreateHashcodeContainer(flow, hashcodeContainersDataDefault());
        assertThat(status.statusCode(), equalTo(200));
        assertThat(status.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    public void uploadHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response status = postUploadHashcodeContainer(flow, hashcodeContainer("hashcode.asice"));
        assertThat(status.statusCode(), equalTo(200));
        assertThat(status.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    public void getHashcodeContainerShouldReturnContainerId() throws NoSuchAlgorithmException, InvalidKeyException {
        Response status = getValidationReportForContainerInSession(flow);
        assertThat(status.statusCode(), equalTo(400));
        assertThat(status.getBody().path(ERROR_CODE), equalTo(SESSION_NOT_FOUND));
    }

    @Test
    public void validateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, IOException, JSONException {
        postUploadHashcodeContainer(flow, hashcodeContainer("hashcode.asice"));
        Response response = getValidationReportForContainerInSession(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("validationConclusion.validSignaturesCount"), equalTo(1));
    }
}
