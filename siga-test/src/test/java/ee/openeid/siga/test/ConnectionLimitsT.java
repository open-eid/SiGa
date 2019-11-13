package ee.openeid.siga.test;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import jodd.util.Base64;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;

public class ConnectionLimitsT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient2Service5();
    }

    @Test
    public void connectionLimitReached() throws Exception {
        List<String> sessions = new ArrayList<>();
        try {
            for (int i = 1; i <= 5; i++) {
                Response validResponse = postCreateContainer(flow, asicContainersDataRequestWithDefault());
                sessions.add(flow.getContainerId());
                Assert.assertEquals("Max connection limit reached before configured value", 200, validResponse.getStatusCode());
            }
            Response errorResponse = postCreateContainer(flow, asicContainersDataRequestWithDefault());
            expectError(errorResponse, 400, CONNECTION_LIMIT_EXCEPTION);
        } finally {
            for (String session : sessions) {
                flow.setContainerId(session);
                deleteContainer(flow);
            }
        }
    }

    @Ignore("Sending big files seems problematic")
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
        List<String> sessions = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            Response validResponse = postUploadContainer(flow, asicContainerRequestFromFile("1385KB_file.asice"));
            sessions.add(flow.getContainerId());
            validResponse.then()
                    .statusCode(200);
        }

        Response errorResponse = postUploadContainer(flow, asicContainerRequestFromFile("1385KB_file.asice"));

        for (String session : sessions) {
            flow.setContainerId(session);
            deleteContainer(flow);
        }

        expectError(errorResponse, 400, CONNECTION_LIMIT_EXCEPTION);
    }

    @Test
    public void requestMaxSizeReached() throws Exception {
        File dataFile = ResourceUtils.getFile("classpath:20mb.jpg");
        byte[] dataFileString = Files.readAllBytes(dataFile.toPath());
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, Base64.encodeToString(dataFileString), DEFAULT_ASICE_CONTAINER_NAME));
        expectError(response, 400, CONNECTION_LIMIT_EXCEPTION);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
