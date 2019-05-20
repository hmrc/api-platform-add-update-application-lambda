package uk.gov.hmrc.apiplatform.upsertapplication

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions.seqAsJavaList

class UpdateApplicationHandlerSpec extends WordSpecLike with Matchers with JsonMapper {

  "Update Application" should {
    "update rateLimit and burstLimit for existing Application when different" in new ExistingLinkedUsagePlanAndAPIKey {
      val usagePlanName = "BRONZE"
      val currentRateLimit: Double = 999
      val currentBurstLimit: Int = 999

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
        .thenReturn(buildMatchingUsagePlanResponse(usagePlanId, applicationName, currentRateLimit, currentBurstLimit))

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

    "not update rateLimit and burstLimit for existing Application when already correct" in new ExistingLinkedUsagePlanAndAPIKey {
      val usagePlanName = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(usagePlanName)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(usagePlanName)._2

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
        .thenReturn(buildMatchingUsagePlanResponse(usagePlanId, applicationName, currentRateLimit, currentBurstLimit))

      addApplicationHandler handleInput(sqsEvent, mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])
      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }
  }

}
