package ee.openeid.siga.test;

import ee.openeid.siga.test.helper.TestBase;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class MonitoringT extends TestBase {

    @Test
    public void getMonitoringStatusAndCheckComponentStatus() throws Exception {
        Response response = getMonitoringStatus();

        response.then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("details.siga.status", equalTo("UP"))
                .body("details.siga.details.ignite.status", equalTo("UP"))
                .body("details.db.status", equalTo("UP"));
    }

    @Test
    public void getMonitoringStatusAndCheckStructure() throws Exception {
        Response response = getMonitoringStatus();

        response.then()
                .statusCode(200)
                .body("status", notNullValue())
                .body("details.siga.status", notNullValue())
                .body("details.siga.details.ignite.status", notNullValue())
                .body("details.siga.details.ignite.details.igniteActiveContainers", notNullValue())
                .body("details.db.status", notNullValue())
                .body("details.db.details.database", notNullValue())
                .body("details.db.details.hello", notNullValue());
    }

    @Step("HTTP GET Monitoring status {0}")
    protected Response getMonitoringStatus() {
        return given()
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .get(createUrl(getContainerEndpoint()))
                .then()
                .log().all()
                .extract()
                .response();
    }

    protected String createUrl(String endpoint) {
        return properties.get("siga.protocol") + "://" + properties.get("siga.hostname") + ":" + properties.get("siga.port") + properties.get("siga.application-context-path") + endpoint;
    }

    @Override
    public String getContainerEndpoint() {
        return MONITORING;
    }
}
