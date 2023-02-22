package ee.openeid.siga.test;

import ee.openeid.siga.test.helper.TestBase;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class MonitoringT extends TestBase {

    private static final String ACCEPT_HEADER_V2 = "application/vnd.spring-boot.actuator.v2+json";

    @Test
    public void getMonitoringStatusAndCheckComponentStatusCurrent() {
        Response response = getMonitoringStatus(null);

        response.then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("components.ignite.status", equalTo("UP"))
                .body("components.metaInfo.status", equalTo("UP"))
                .body("components.siva.status", equalTo("UP"))
                .body("components.db.status", equalTo("UP"));
    }

    @Test
    public void getMonitoringStatusAndCheckComponentStatusV2() {
        Response response = getMonitoringStatus(ACCEPT_HEADER_V2);

        response.then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("details.ignite.status", equalTo("UP"))
                .body("details.metaInfo.status", equalTo("UP"))
                .body("details.siva.status", equalTo("UP"))
                .body("details.db.status", equalTo("UP"));
    }

    @Test
    public void getMonitoringStatusAndCheckStructureCurrent() {
        Response response = getMonitoringStatus(null);

        response.then()
                .statusCode(200)
                .body("status", notNullValue())
                .body("components.ignite.status", notNullValue())
                .body("components.ignite.status", notNullValue())
                .body("components.ignite.details.igniteActiveContainers", notNullValue())
                .body("components.db.status", notNullValue())
                .body("components.db.details.database", notNullValue())
                .body("components.db.details.validationQuery", notNullValue())
                .body("components.metaInfo.status", notNullValue())
                .body("components.metaInfo.details.webappName", notNullValue())
                .body("components.metaInfo.details.version", notNullValue())
                .body("components.metaInfo.details.buildTime", notNullValue())
                .body("components.metaInfo.details.startTime", notNullValue())
                .body("components.metaInfo.details.currentTime", notNullValue())
                .body("components.siva.status", notNullValue());
    }

    @Test
    public void getMonitoringStatusAndCheckStructureV2() {
        Response response = getMonitoringStatus(ACCEPT_HEADER_V2);

        response.then()
                .statusCode(200)
                .body("status", notNullValue())
                .body("details.ignite.status", notNullValue())
                .body("details.ignite.status", notNullValue())
                .body("details.ignite.details.igniteActiveContainers", notNullValue())
                .body("details.db.status", notNullValue())
                .body("details.db.details.database", notNullValue())
                .body("details.db.details.validationQuery", notNullValue())
                .body("details.metaInfo.status", notNullValue())
                .body("details.metaInfo.details.webappName", notNullValue())
                .body("details.metaInfo.details.version", notNullValue())
                .body("details.metaInfo.details.buildTime", notNullValue())
                .body("details.metaInfo.details.startTime", notNullValue())
                .body("details.metaInfo.details.currentTime", notNullValue())
                .body("details.siva.status", notNullValue());
    }

    @Test
    public void getVersionInfoStatus() {
        Response response = getVersionInfo();

        response.then()
                .statusCode(200)
                .body("version", notNullValue());
    }

    @Step("HTTP GET Monitoring status {0}")
    protected Response getMonitoringStatus(String accept) {
        RequestSpecification requestSpecification =  given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON);
        if (accept != null) {
            requestSpecification = requestSpecification.accept(accept);
        }
        return requestSpecification.when()
                .get(createUrl(getContainerEndpoint()))
                .then()
                .log().all()
                .extract()
                .response();
    }

    @Step("HTTP GET version info {0}")
    protected Response getVersionInfo() {
        return given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .get(createUrl(VERSION))
                .then()
                .log().all()
                .extract()
                .response();
    }

    @Override
    protected String createUrl(String endpoint) {
        return properties.get("siga.protocol") + "://" + properties.get("siga.hostname") + ":" + properties.get("siga.port") + properties.get("siga.application-context-path") + endpoint;
    }

    @Override
    public String getContainerEndpoint() {
        return MONITORING;
    }
}
