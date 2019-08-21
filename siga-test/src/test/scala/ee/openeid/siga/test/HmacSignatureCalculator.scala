package ee.openeid.siga.test

import java.time.Instant
import io.gatling.core.session.Session
import io.gatling.http.client.{Request, SignatureCalculator}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Hex

class HmacSignatureCalculator(val session: Session) extends SignatureCalculator {
  private final val X_AUTHORIZATION_TIMESTAMP = "X-Authorization-Timestamp"
  private final val X_AUTHORIZATION_SERVICE_UUID = "X-Authorization-ServiceUUID"
  private final val X_AUTHORIZATION_SIGNATURE = "X-Authorization-Signature"
  private final val DELIMITER = ":"

  override def sign(request: Request): Unit = {
    val serviceUuid = session("serviceUuid").as[String]
    val signingSecret = session("signingSecret").as[String]
    val hmac = Mac.getInstance("HmacSHA256")
    val timestamp = String.valueOf(Instant.now.getEpochSecond)
    val requestMethod = request.getMethod.name
    val uri = request.getUri.getPath.replaceAll("/siga", "")
    hmac.init(new SecretKeySpec(signingSecret.getBytes("UTF-8"), "HmacSHA256"))
    hmac.update((serviceUuid + DELIMITER + timestamp + DELIMITER + requestMethod + DELIMITER + uri + DELIMITER).getBytes("UTF-8"))
    if (request.getBody != null) {
      val payload = request.getBody.getBytes
      hmac.update(payload)
    }
    val signature = Hex.encodeHexString(hmac.doFinal())
    hmac.reset()
    request.getHeaders.add(X_AUTHORIZATION_TIMESTAMP, timestamp)
    request.getHeaders.add(X_AUTHORIZATION_SERVICE_UUID, serviceUuid)
    request.getHeaders.add(X_AUTHORIZATION_SIGNATURE, signature)
  }
}
