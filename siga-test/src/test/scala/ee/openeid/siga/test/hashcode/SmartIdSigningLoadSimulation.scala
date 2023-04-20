package ee.openeid.siga.test.hashcode

import ee.openeid.siga.test.{BaseSimulation, HmacSignatureCalculator}

import java.io.FileInputStream
import java.util.Properties
import ee.openeid.siga.test.utils.RequestBuilder.{hashcodeContainersDataRequestWithDefault, smartIdSigningRequestWithDefault}
import ee.openeid.siga.test.utils.{DigestSigner, RequestBuilder}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.{status, _}
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class SmartIdSigningLoadSimulation extends BaseSimulation {
  private final val HC_SID_SIGNING_INIT: String = "/hashcodecontainers/${containerId}/smartidsigning"
  private final val HC_SID_SIGNING_STATUS: String = "/hashcodecontainers/${containerId}/smartidsigning/${generatedSignatureId}/status"

  private val loadTestScenario: ScenarioBuilder = scenario("Hashcode container SmartId signing flow load test")
    .feed(uuidFeeder)
    .exec(hcCreateContainer)
    .doIf("${containerId.exists()}") {
      exec(hcSmartIdSigningInit)
        .doIf("${generatedSignatureId.exists()}") {
          doWhileDuring(session => session("sidStatus").as[String].equals("OUTSTANDING_TRANSACTION"), 30 seconds, "counter", false) {
            exec(hcSmartIdSigningStatus)
              .doIf(session => session("sidStatus").as[String].equals("OUTSTANDING_TRANSACTION")) {
                pause(3000 millis)
              }
          }
          .exitHereIfFailed
          .exec(hcGetContainer).exitHereIfFailed
          .exec(hcDeleteContainer)
        }
    }

  def hcSmartIdSigningInit = {
    http("SID_SIGNING_INIT")
      .post(HC_SID_SIGNING_INIT)
      .body(StringBody(smartIdSigningRequestWithDefault("LT", "PNOEE-30303039914-1Q3P-Q").toString)).asJson
      .check(
        status.is(200),
        jsonPath("$.generatedSignatureId").optional.saveAs("generatedSignatureId")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def hcSmartIdSigningStatus = {
    http("SID_SIGNING_STATUS")
      .get(HC_SID_SIGNING_STATUS)
      .check(status.is(200),
        jsonPath("$.sidStatus").saveAs("sidStatus")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  setUp(loadTestScenario.inject(
    rampUsersPerSec(0) to 5 during (90 seconds),
    constantUsersPerSec(5) during (5 minutes)))
    .protocols(httpProtocol)
    .assertions(
      details("CREATE_CONTAINER").responseTime.mean.lt(500),
      details("CREATE_CONTAINER").successfulRequests.percent.gte(99.9),
      details("SID_SIGNING_INIT").responseTime.mean.lt(150),
      details("SID_SIGNING_INIT").successfulRequests.percent.gte(99.9),
      details("SID_SIGNING_STATUS").responseTime.mean.lt(150),
      details("GET_CONTAINER").responseTime.mean.lt(150),
      details("GET_CONTAINER").successfulRequests.percent.gte(99.9),
      details("DELETE_CONTAINER").responseTime.mean.lt(150),
      details("DELETE_CONTAINER").successfulRequests.percent.gte(99.9),
    )
}
