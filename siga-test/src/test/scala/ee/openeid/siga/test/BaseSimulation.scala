package ee.openeid.siga.test

import ee.openeid.siga.test.utils.RequestBuilder.{asicContainersDataRequestWithDefault, hashcodeContainersDataRequestWithDefault}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.{status, _}
import io.gatling.http.protocol.HttpProtocolBuilder

import java.io.FileInputStream
import java.util.Properties

abstract class BaseSimulation extends Simulation {
  private final val ASIC_CREATE_CONTAINER_ENDPOINT: String = "/containers"
  private final val ASIC_GET_SIGNATURES_LIST: String = "/containers/${containerId}/signatures"
  private final val ASIC_VALIDATE_CONTAINER_BY_ID: String = "/containers/${containerId}/validationreport"
  private final val ASIC_GET_CONTAINER: String = "/containers/${containerId}"
  private final val ASIC_DELETE_CONTAINER: String = "/containers/${containerId}"
  private final val HC_CREATE_CONTAINER_ENDPOINT: String = "/hashcodecontainers"
  private final val HC_GET_SIGNATURES_LIST: String = "/hashcodecontainers/${containerId}/signatures"
  private final val HC_VALIDATE_CONTAINER_BY_ID: String = "/hashcodecontainers/${containerId}/validationreport"
  private final val HC_GET_CONTAINER: String = "/hashcodecontainers/${containerId}"
  private final val HC_DELETE_CONTAINER: String = "/hashcodecontainers/${containerId}"

  protected val randomUuidFeeder = Iterator.continually(Map("serviceUuid" -> java.util.UUID.randomUUID.toString()))
  protected val uuidFeeder = Array(Map("serviceUuid" -> "a7fd7728-a3ea-4975-bfab-f240a67e894f", "signingSecret" -> "746573745365637265744b6579303031"),
    Map("serviceUuid" -> "824dcfe9-5c26-4d76-829a-e6630f434746", "signingSecret" -> "746573745365637265744b6579303032"),
    Map("serviceUuid" -> "400ff9a2-b5fb-4fde-b764-9b519963f82e", "signingSecret" -> "746573745365637265744b6579303033")).circular

  protected val httpProtocol: HttpProtocolBuilder = {
    val prop = new Properties()
    prop.load(new FileInputStream("src/test/resources/application-test.properties"))
    val host = prop.getProperty("siga.hostname")
    val protocol = prop.getProperty("siga.protocol")
    val port = prop.getProperty("siga.port")
    val applicationContextPath = prop.getProperty("siga.application-context-path")
    val url = protocol + "://" + host + ":" + port + applicationContextPath
    http.baseUrl(url).contentTypeHeader("application/json")
  }

  def asicCreateContainer = {
    http("CREATE_CONTAINER")
      .post(ASIC_CREATE_CONTAINER_ENDPOINT)
      .body(StringBody(asicContainersDataRequestWithDefault().toString)).asJson
      .check(
        status.is(200),
        jsonPath("$.containerId").optional.saveAs("containerId"))
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcCreateContainer = {
    http("CREATE_CONTAINER")
      .post(HC_CREATE_CONTAINER_ENDPOINT)
      .body(StringBody(hashcodeContainersDataRequestWithDefault().toString)).asJson
      .check(
        status.is(200),
        jsonPath("$.containerId").optional.saveAs("containerId"))
      .sign(session => new HmacSignatureCalculator(session))
  }

  def asicGetSignaturesList = {
    getSignaturesList(ASIC_GET_SIGNATURES_LIST)
  }

  def hcGetSignaturesList = {
    getSignaturesList(HC_GET_SIGNATURES_LIST)
  }

  def getSignaturesList(endpoint : String) = {
    http("GET_SIGNATURES_LIST")
      .get(endpoint)
      .check(
        status.is(200),
        jsonPath("$.signatures").optional.saveAs("signatures")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def asicValidateContainerById = {
    validateContainerById(ASIC_VALIDATE_CONTAINER_BY_ID)
  }

  def hcValidateContainerById = {
    validateContainerById(HC_VALIDATE_CONTAINER_BY_ID)
  }

  def validateContainerById(endpoint : String) = {
    http("VALIDATE_CONTAINER_BY_ID")
      .get(endpoint)
      .check(status.is(200),
        jsonPath("$.validationConclusion").optional.saveAs("validationConclusion")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def asicGetContainer = {
    getContainer(ASIC_GET_CONTAINER)
  }

  def hcGetContainer = {
    getContainer(HC_GET_CONTAINER)
  }

  def getContainer(endpoint : String) = {
    http("GET_CONTAINER")
      .get(endpoint)
      .check(status.is(200))
      .sign(session => new HmacSignatureCalculator(session))
  }

  def asicDeleteContainer = {
    deleteContainer(ASIC_DELETE_CONTAINER)
  }

  def hcDeleteContainer = {
    deleteContainer(HC_DELETE_CONTAINER)
  }

  def deleteContainer(endpoint : String) = {
    http("DELETE_CONTAINER")
      .delete(endpoint)
      .check(
        status.is(200),
        jsonPath("$.result").optional.saveAs("hcDeleteContainerStatus"))
      .sign(session => new HmacSignatureCalculator(session))
  }
}