package ee.openeid.siga.test.asic

import ee.openeid.siga.test.{BaseSimulation, HmacSignatureCalculator}

import java.io.FileInputStream
import java.util.Properties
import ee.openeid.siga.test.utils.RequestBuilder.{hashcodeContainersDataRequestWithDefault, midSigningRequestWithDefault}
import ee.openeid.siga.test.utils.{DigestSigner, RequestBuilder}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.{status, _}
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

class MobileIdSigningLoadSimulation extends BaseSimulation {
  private final val ASIC_MID_SIGNING_INIT: String = "/containers/${containerId}/mobileidsigning"
  private final val ASIC_MID_SIGNING_STATUS: String = "/containers/${containerId}/mobileidsigning/${generatedSignatureId}/status"

  private val loadTestScenario: ScenarioBuilder = scenario("Asic container MobileId signing flow load test")
    .feed(uuidFeeder)
      .exec(asicCreateContainer)
        .doIf("${containerId.exists()}") {
          exec(asicMobileIdSigningInit)
            .doIf("${generatedSignatureId.exists()}") {
              doWhileDuring(session => session("midStatus").as[String].equals("OUTSTANDING_TRANSACTION"), 30 seconds, "counter", false) {
                exec(asicMobileIdSigningStatus)
                .doIf(session => session("midStatus").as[String].equals("OUTSTANDING_TRANSACTION")) {
                  pause(3000 millis)
                }
              }
              .exitHereIfFailed
              .exec(asicGetContainer).exitHereIfFailed
              .exec(asicDeleteContainer)
            }
        }

  def asicMobileIdSigningInit = {
    http("MID_SIGNING_INIT")
      .post(ASIC_MID_SIGNING_INIT)
      .body(StringBody(midSigningRequestWithDefault("60001019906", "+37200000766", "LT").toString)).asJson
      .check(
        status.is(200),
        jsonPath("$.generatedSignatureId").optional.saveAs("generatedSignatureId")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def asicMobileIdSigningStatus = {
    http("MID_SIGNING_STATUS")
      .get(ASIC_MID_SIGNING_STATUS)
      .check(status.is(200),
        jsonPath("$.midStatus").saveAs("midStatus")
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
      details("MID_SIGNING_INIT").responseTime.mean.lt(150),
      details("MID_SIGNING_INIT").successfulRequests.percent.gte(99.9),
      details("MID_SIGNING_STATUS").responseTime.mean.lt(150),
      details("GET_CONTAINER").responseTime.mean.lt(150),
      details("GET_CONTAINER").successfulRequests.percent.gte(99.9),
      details("DELETE_CONTAINER").responseTime.mean.lt(150),
      details("DELETE_CONTAINER").successfulRequests.percent.gte(99.9),
    )
}
