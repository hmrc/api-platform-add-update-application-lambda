package uk.gov.hmrc.apiplatform.upsertapplication

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions.seqAsJavaList

class UpdateApplicationHandlerSpec extends WordSpecLike with Matchers with MockitoSugar with JsonMapper {

  trait Setup {
    def buildAddApplicationRequest(applicationName: String, usagePlan: String, serverToken: String): SQSMessage = {
      val message = new SQSMessage()
      message.setBody(s"""{"applicationName": "$applicationName", "usagePlan": "$usagePlan", "serverToken": "$serverToken"}""")

      message
    }

    def buildMatchingUsagePlansResponse(matchingId: String, matchingName: String): GetUsagePlansResponse = {
      GetUsagePlansResponse.builder()
        .items(UsagePlan.builder().id(matchingId).name(matchingName).build())
        .build()
    }

    def buildMatchingAPIKeysResponse(matchingAPIKeyId: String, matchingApplicationName: String, matchingAPIKey: String): GetApiKeysResponse = {
      GetApiKeysResponse.builder().items(List(ApiKey.builder().id(matchingAPIKeyId).name(matchingApplicationName).value(matchingAPIKey).build())).build()
    }

    def buildMatchingUsagePlanKeysResponse(matchingUsagePlanId: String, matchingAPIKeyId: String): GetUsagePlanKeysResponse = {
      GetUsagePlanKeysResponse.builder().items(UsagePlanKey.builder().id(matchingAPIKeyId).value(matchingUsagePlanId).build()).build()
    }

    val usagePlanId: String = UUID.randomUUID().toString
    val apiKeyId: String = UUID.randomUUID().toString

    val applicationName = "test-application"
    val serverToken = "foo-bar-baz"

    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val mockContext: Context = mock[Context]
    when(mockContext.getLogger).thenReturn(mock[LambdaLogger])

    val environment: Map[String, String] = Map()

    when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildMatchingUsagePlansResponse(usagePlanId, applicationName))
    when(mockAPIGatewayClient.getApiKeys(any[GetApiKeysRequest])).thenReturn(buildMatchingAPIKeysResponse(apiKeyId, applicationName, serverToken))
    when(mockAPIGatewayClient.getUsagePlanKeys(any[GetUsagePlanKeysRequest])).thenReturn(buildMatchingUsagePlanKeysResponse(usagePlanId, apiKeyId))

    val addApplicationHandler = new UpsertApplicationHandler(mockAPIGatewayClient, environment)
  }

  "Update Application" should {
    "update rateLimit and burstLimit for existing Application" in new Setup {
      val usagePlanName = "BRONZE"

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      when(mockAPIGatewayClient.updateUsagePlan(updateUsagePlanRequestCaptor.capture())).thenReturn(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 2

      capturedUpdateRequest.patchOperations() should contain only (
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/rateLimit").value(addApplicationHandler.NamedUsagePlans(usagePlanName)._1.toString).build(),
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/burstLimit").value(addApplicationHandler.NamedUsagePlans(usagePlanName)._2.toString).build())

      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }
  }
}
