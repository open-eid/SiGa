package ee.openeid.siga.test.asic

import ee.openeid.siga.test.{BaseSimulation, HmacSignatureCalculator}
import ee.openeid.siga.test.helper.TestData.SIGNER_CERT_ESTEID2018_PEM
import ee.openeid.siga.test.utils.RequestBuilder.{hashcodeContainersDataRequestWithDefault, remoteSigningRequestWithDefault}
import ee.openeid.siga.test.utils.{DigestSigner, RequestBuilder}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.{status, _}

import scala.concurrent.duration._

class RemoteSigningLoadSimulation extends BaseSimulation {
  private final val ASIC_REMOTE_SIGNING_INIT: String = "/containers/${containerId}/remotesigning"
  private final val ASIC_REMOTE_SIGNING_FINISH: String = "/containers/${containerId}/remotesigning/${generatedSignatureId}"

  private val loadTestScenario: ScenarioBuilder = scenario("Asic container remote signing flow load test")
    .feed(uuidFeeder)
    .exec(asicCreateContainer)
    .doIf("${containerId.exists()}") {
      exec(asicRemoteSigningInit)
      .exec(session => remoteSigningFinishRequest(session))
        .exec(asicRemoteSigningFinish)
        .exitHereIfFailed
      .exec(asicGetContainer).exitHereIfFailed
      .exec(asicValidateContainerById).exitHereIfFailed
      .exec(asicDeleteContainer)
    }

  def asicRemoteSigningInit = {
    http("REMOTE_SIGNING_INIT")
      .post(ASIC_REMOTE_SIGNING_INIT)
      .body(StringBody(remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT").toString)).asJson
      .check(
        status.is(200),
        jsonPath("$.generatedSignatureId").saveAs("generatedSignatureId"),
        jsonPath("$.dataToSign").saveAs("dataToSign"),
        jsonPath("$.digestAlgorithm").saveAs("digestAlgorithm")
      )
      .sign(session => new HmacSignatureCalculator(session))
  }

  def remoteSigningFinishRequest(session: Session) = {
    val dataToSign = session("dataToSign").as[String]
    val digestAlgorithm = session("digestAlgorithm").as[String]
    session.set("remoteSigningFinishRequest", RequestBuilder.remoteSigningSignatureValueRequest(DigestSigner.signDigest(dataToSign, digestAlgorithm)).toString())
  }

  def asicRemoteSigningFinish = {
    http("REMOTE_SIGNING_FINISH")
      .put(ASIC_REMOTE_SIGNING_FINISH)
      .body(StringBody("${remoteSigningFinishRequest}")).asJson
      .check(
        status.is(200),
        jsonPath("$.result").is("OK")
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
      details("REMOTE_SIGNING_INIT").responseTime.mean.lt(150),
      details("REMOTE_SIGNING_INIT").successfulRequests.percent.gte(99.9),
      details("REMOTE_SIGNING_FINISH").responseTime.mean.lt(1000),
      details("REMOTE_SIGNING_FINISH").successfulRequests.percent.gte(99.9),
      details("GET_CONTAINER").responseTime.mean.lt(150),
      details("GET_CONTAINER").successfulRequests.percent.gte(99.9),
      details("VALIDATE_CONTAINER_BY_ID").responseTime.mean.lt(500),
      details("VALIDATE_CONTAINER_BY_ID").successfulRequests.percent.gte(99.9),
      details("DELETE_CONTAINER").responseTime.mean.lt(150),
      details("DELETE_CONTAINER").successfulRequests.percent.gte(99.9),
    )
}
