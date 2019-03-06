package ee.openeid.siga.test;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import ee.openeid.siga.test.utils.RequestBuilder;


import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainer;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class HashcodeST extends TestBase{

    @Test
    public void createHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response status = postCreateHashcodeContainer(hashcodeContainersDataDefault());
        assertThat(status.statusCode(), equalTo(200));
        assertThat(status.getBody().path("containerId").toString().length(), equalTo(36));
    }

    @Test
    public void uploadHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response status = postUploadHashcodeContainer(hashcodeContainer("hashcode.asice"));
        assertThat(status.statusCode(), equalTo(200));
        assertThat(status.getBody().path("containerId").toString().length(), equalTo(36));
    }

    @Test
    public void getHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response status = getValidationReportForContainerInSession("sasadsa");
        assertThat(status.statusCode(), equalTo(400));
        assertThat(status.getBody().path("errorCode"), equalTo("SESSION_NOT_FOUND"));
    }
}
