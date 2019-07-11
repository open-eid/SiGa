package ee.openeid.siga.test.statistics;

import ee.openeid.siga.common.Result;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.json.JSONException;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import static ee.openeid.siga.common.event.SigaEventName.*;
import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore
public class RemoteSigningEventsT extends StatisticsBaseT {
    private static HashMap<String, Response> dataToSignResponses = new HashMap<>();

    static {
        NR_OF_CONTAINERS_GENERATED = 3;
    }

    @Test
    public void test0_shouldPassRemoteSigningFlow() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        SigaApiFlow service1 = SigaApiFlow.buildForTestClient1Service1();
        remoteSigningFlowFor(service1);
        SigaApiFlow service12 = SigaApiFlow.buildForTestClient1Service2();
        remoteSigningFlowFor(service12);
        SigaApiFlow service3 = SigaApiFlow.buildForTestClient2Service3();
        remoteSigningFlowFor(service3);
    }

    private void remoteSigningFlowFor(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException, JSONException, IOException {
        Response response = postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        assertThat(response.statusCode(), equalTo(200));
        String containerId = response.getBody().path(CONTAINER_ID).toString();
        flow.setContainerId(containerId);
        assertThat(containerId.length(), equalTo(36));
        containerIds.add(containerId);

        response = getSignatureList(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", response.getBody().path("signatures[0].id"));

        Response dataToSign = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        assertThat(dataToSign.statusCode(), equalTo(200));
        assertThat(dataToSign.getBody().path(DATA_TO_SIGN).toString().length(), greaterThanOrEqualTo(1500));
        assertThat(dataToSign.getBody().path(DIGEST_ALGO), equalTo("SHA512"));
        dataToSignResponses.put(flow.getContainerId(), dataToSign);

        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = dataToSignResponses.get(flow.getContainerId()).as(CreateHashcodeContainerRemoteSigningResponse.class);
        assertNotNull(dataToSignResponse);
        response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo(Result.OK.name()));

        Response validationResponse = getValidationReportForContainerInSession(flow);
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(2));

        response = deleteContainer(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo(Result.OK.name()));
    }

    @Test
    public void test1_queryResultShouldEqual_HcUploadContainerRequestsMade() {
        await().atMost(ELASTIC_QUERY_TIMEOUT, SECONDS).with().pollInterval(ELASTIC_QUERY_POLL_INTERVAL, SECONDS).until(() -> {
            QueryBuilder query = createQueryForSuccessEvent(HC_UPLOAD_CONTAINER);
            SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
            return checkSearchResponse(response);
        });
    }

    @Test
    public void test2_queryResultShouldEqual_HcGetSignatureListRequestsMade() {
        QueryBuilder query = createQueryForSuccessEvent(HC_GET_SIGNATURES_LIST);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        checkSearchResponse(response);
    }

    @Test
    public void test3_queryResultShouldEqual_HcMobileIdSigningInitRequestsMade() {
        QueryBuilder query = createQueryForSuccessEvent(HC_REMOTE_SIGNING_INIT);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        assertEquals(NR_OF_CONTAINERS_GENERATED, countMatchingParameters(response, EventParam.SIGNATURE_PROFILE, "LT"));
        checkSearchResponse(response);
    }

    @Test
    public void test4_queryResultShouldEqual_HcRemoteSigningFinishRequestsMade() {
        QueryBuilder query = createQueryForSuccessEvent(HC_REMOTE_SIGNING_FINISH);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        checkSearchResponse(response);
    }

    @Test
    public void test5_queryResultShouldEqual_FinalizeSignatureRequestsMade() {
        QueryBuilder query = createQueryForSuccessEvent(FINALIZE_SIGNATURE);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        assertEquals(NR_OF_CONTAINERS_GENERATED, response.getHits().totalHits);
        assertEquals(NR_OF_CONTAINERS_GENERATED, countMatchingParametersWithAnyValue(response, EventParam.SIGNATURE_ID));
    }

    @Test
    public void test6_queryResultShouldEqual_TsaRequestMade() {
        QueryBuilder query = createQueryForSuccessEvent(TSA_REQUEST);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        assertEquals(NR_OF_CONTAINERS_GENERATED, countMatchingParameters(response, EventParam.REQUEST_URL, "http://demo.sk.ee/tsa"));
        assertEquals(NR_OF_CONTAINERS_GENERATED, countMatchingParameters(response, EventParam.ISSUING_CA, "C=EE,O=AS Sertifitseerimiskeskus,CN=TEST of EE Certification Centre Root CA,E=pki@sk.ee"));
        assertEquals(NR_OF_CONTAINERS_GENERATED, response.getHits().totalHits);
    }

    @Test
    public void test7_queryResultShouldEqual_OcspRequestMade() {
        QueryBuilder query = createQueryForSuccessEvent(OCSP_REQUEST);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        assertEquals(NR_OF_CONTAINERS_GENERATED, countMatchingParameters(response, EventParam.REQUEST_URL, "http://aia.demo.sk.ee/esteid2018"));
        assertEquals(NR_OF_CONTAINERS_GENERATED, countMatchingParameters(response, EventParam.ISSUING_CA, "C=EE,O=SK ID Solutions AS,2.5.4.97=NTREE-10747013,CN=TEST of ESTEID2018"));
        assertEquals(NR_OF_CONTAINERS_GENERATED, response.getHits().totalHits);
    }

    @Test
    public void test8_queryResultShouldEqual_HcValidateContainerByIdRequestsMade() {
        QueryBuilder query = createQueryForSuccessEvent(HC_VALIDATE_CONTAINER_BY_ID);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        checkSearchResponse(response);
    }

    @Test
    public void test9_queryResultShouldEqual_HcDeleteContainerRequestsMade() {
        QueryBuilder query = createQueryForSuccessEvent(HC_DELETE_CONTAINER);
        SearchResponse response = prepareSearchRequestFor(query).execute().actionGet();
        checkSearchResponse(response);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
