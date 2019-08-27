package ee.openeid.siga.test;

import ee.openeid.siga.test.utils.RequestBuilder;
import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static ee.openeid.siga.test.helper.TestData.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class MonitoringT {

    protected static Properties properties;

    static {
        properties = new Properties();
        try {
            ClassLoader classLoader = RequestBuilder.class.getClassLoader();
            String path = classLoader.getResource("application-test.properties").getPath();
            properties.load(new FileInputStream(new File(path)));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.filters(new AllureRestAssured());
    }

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
                .get(getMonitoringUrl())
                .then()
                .log().all()
                .extract()
                .response();
    }

    protected String getMonitoringUrl() {
        return properties.get("siga.protocol") + "://" + properties.get("siga.hostname") + ":" + properties.get("siga.port") + properties.get("siga.application-context-path") + MONITORING;
    }
}
