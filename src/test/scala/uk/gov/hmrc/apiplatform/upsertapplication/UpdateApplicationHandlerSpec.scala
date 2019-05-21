package uk.gov.hmrc.apiplatform.upsertapplication

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions.seqAsJavaList

class UpdateApplicationHandlerSpec extends WordSpecLike with Matchers with JsonMapper {

  val TestAPIs: Map[String, (String, String)] =
    Map(
      "test--api--1.0" -> (UUID.randomUUID().toString, "current"),
      "test--api--2.0" -> (UUID.randomUUID().toString, "current")
    )

  def asApiStage(apiName: String): ApiStage = ApiStage.builder().apiId(TestAPIs(apiName)._1).stage(TestAPIs(apiName)._2).build()
  def asRestApi(apiName: String): RestApi = RestApi.builder().id(TestAPIs(apiName)._1).name(apiName).build()
  def asApiStageString(apiName: String) = s"${TestAPIs(apiName)._1}:${TestAPIs(apiName)._2}"

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

    "update Usage Plan to include missing Subscriptions" in new ExistingLinkedUsagePlanAndAPIKey {
      val usagePlanName = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(usagePlanName)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(usagePlanName)._2

      val requestedSubscriptions: Seq[String] = Seq("test--api--1.0", "test--api--2.0")

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken, requestedSubscriptions)))

      when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest]))
        .thenReturn(
          GetRestApisResponse.builder()
            .items(
              asRestApi("test--api--1.0"),
              asRestApi("test--api--2.0"))
            .build())

      when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
        .thenReturn(
          buildMatchingUsagePlanResponse(
            usagePlanId,
            applicationName,
            currentRateLimit,
            currentBurstLimit,
            Some(Seq(asApiStage("test--api--1.0")))))

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      when(mockAPIGatewayClient.updateUsagePlan(updateUsagePlanRequestCaptor.capture())).thenReturn(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 1

      capturedUpdateRequest.patchOperations() should contain only
        PatchOperation.builder().op(Op.ADD).path("/apiStages").value(asApiStageString("test--api--2.0")).build()

      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }

    "update Usage Plan to remove non-required Subscriptions" in new ExistingLinkedUsagePlanAndAPIKey {
      val usagePlanName = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(usagePlanName)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(usagePlanName)._2

      val requestedSubscriptions: Seq[String] = Seq("test--api--2.0")

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken, requestedSubscriptions)))

      when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest]))
        .thenReturn(
          GetRestApisResponse.builder()
            .items(
              asRestApi("test--api--1.0"),
              asRestApi("test--api--2.0"))
            .build())

      when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
        .thenReturn(
          buildMatchingUsagePlanResponse(
            usagePlanId,
            applicationName,
            currentRateLimit,
            currentBurstLimit,
            Some(Seq(asApiStage("test--api--1.0"), asApiStage("test--api--2.0")))))

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      when(mockAPIGatewayClient.updateUsagePlan(updateUsagePlanRequestCaptor.capture())).thenReturn(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 1

      capturedUpdateRequest.patchOperations() should contain only
        PatchOperation.builder().op(Op.REMOVE).path("/apiStages").value(asApiStageString("test--api--1.0")).build()

      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }

    "not update Usage Plan where all Subscriptions already exist" in new ExistingLinkedUsagePlanAndAPIKey {
      val usagePlanName = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(usagePlanName)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(usagePlanName)._2

      val requestedSubscriptions: Seq[String] = Seq("test--api--1.0", "test--api--2.0")

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken, requestedSubscriptions)))

      when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest]))
        .thenReturn(
          GetRestApisResponse.builder()
            .items(
              asRestApi("test--api--1.0"),
              asRestApi("test--api--2.0"))
            .build())

      when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
        .thenReturn(
          buildMatchingUsagePlanResponse(
            usagePlanId,
            applicationName,
            currentRateLimit,
            currentBurstLimit,
            Some(Seq(asApiStage("test--api--1.0"), asApiStage("test--api--2.0")))))

      addApplicationHandler handleInput(sqsEvent, mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])
      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      verify(mockAPIGatewayClient, times(0)).createUsagePlanKey(any[CreateUsagePlanKeyRequest])
    }
  }

}
