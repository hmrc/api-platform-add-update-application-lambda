package uk.gov.hmrc.apiplatform.upsertapplication

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify}
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

class UpdateApplicationHandlerSpec extends WordSpecLike with Matchers with JsonMapper {

  "Update Application" should {
    "update rateLimit and burstLimit for existing Application when different" in new ExistingLinkedUsagePlanAndAPIKey {
      val requestedUsagePlan = "BRONZE"
      mocksReturnUsagePlan(999, 999)

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] =
        callsToUpdateUsagePlanCaptured(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 2

      capturedUpdateRequest.patchOperations() should contain only (
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/rateLimit").value(addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1.toString).build(),
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/burstLimit").value(addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2.toString).build())

      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }

    "not update rateLimit and burstLimit for existing Application when already correct" in new ExistingLinkedUsagePlanAndAPIKey {
      val requestedUsagePlan = "BRONZE"

      mocksReturnUsagePlan(
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2)

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])
      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }

    "update Usage Plan to include missing Subscriptions" in new ExistingLinkedUsagePlanAndAPIKey {
      val requestedUsagePlan = "BRONZE"

      mocksReturnRestApis(Seq("test--api--1.0", "test--api--2.0"))
      mocksReturnUsagePlan(
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        Seq("test--api--1.0"))

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] =
        callsToUpdateUsagePlanCaptured(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      addApplicationHandler.handleInput(
        buildSQSEventWithSingleRequest(
          applicationName,
          requestedUsagePlan,
          serverToken,
          Seq("test--api--1.0", "test--api--2.0")),
        mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 1

      capturedUpdateRequest.patchOperations() should contain only
        PatchOperation.builder().op(Op.ADD).path("/apiStages").value(asApiStageString("test--api--2.0")).build()

      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }

    "update Usage Plan to remove non-required Subscriptions" in new ExistingLinkedUsagePlanAndAPIKey {
      val requestedUsagePlan = "BRONZE"

      mocksReturnRestApis(Seq("test--api--1.0", "test--api--2.0"))
      mocksReturnUsagePlan(
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        Seq("test--api--1.0", "test--api--2.0"))

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] =
        callsToUpdateUsagePlanCaptured(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      addApplicationHandler.handleInput(
        buildSQSEventWithSingleRequest(
          applicationName,
          requestedUsagePlan,
          serverToken,
          Seq("test--api--2.0")),
        mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 1

      capturedUpdateRequest.patchOperations() should contain only
        PatchOperation.builder().op(Op.REMOVE).path("/apiStages").value(asApiStageString("test--api--1.0")).build()

      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }

    "not update Usage Plan where all Subscriptions already exist" in new ExistingLinkedUsagePlanAndAPIKey {
      val requestedUsagePlan = "BRONZE"

      mocksReturnRestApis(Seq("test--api--1.0", "test--api--2.0"))
      mocksReturnUsagePlan(
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        Seq("test--api--1.0", "test--api--2.0"))

      addApplicationHandler.handleInput(
        buildSQSEventWithSingleRequest(
          applicationName,
          requestedUsagePlan,
          serverToken,
          Seq("test--api--1.0", "test--api--2.0")),
        mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])
      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }
  }

}
