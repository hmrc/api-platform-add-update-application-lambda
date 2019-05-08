package uk.gov.hmrc.apiplatform.upsertapplication

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, verifyZeroInteractions, when}
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions._

class AddApplicationHandlerSpec extends WordSpecLike with Matchers with JsonMapper {

  "Add Application" should {
    "create a new Bronze API Gateway Usage Plan if the application is new" in new NoMatchingUsagePlan {
      val usagePlanName = "BRONZE"

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanRequest])
      when(mockAPIGatewayClient.createUsagePlan(createUsagePlanRequestCaptor.capture())).thenReturn(CreateUsagePlanResponse.builder().id(usagePlanId).build())

      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = ArgumentCaptor.forClass(classOf[CreateApiKeyRequest])
      when(mockAPIGatewayClient.createApiKey(createApiKeyRequestCaptor.capture())).thenReturn(CreateApiKeyResponse.builder().id(apiKeyId).build())

      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanKeyRequest])
      when(mockAPIGatewayClient.createUsagePlanKey(createUsagePlanKeyRequestCaptor.capture())).thenReturn(CreateUsagePlanKeyResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(usagePlanName)._1,
        addApplicationHandler.NamedUsagePlans(usagePlanName)._2)
      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create a new Silver API Gateway Usage Plan if the application is new" in new NoMatchingUsagePlan {
      val usagePlanName = "SILVER"

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanRequest])
      when(mockAPIGatewayClient.createUsagePlan(createUsagePlanRequestCaptor.capture())).thenReturn(CreateUsagePlanResponse.builder().id(usagePlanId).build())

      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = ArgumentCaptor.forClass(classOf[CreateApiKeyRequest])
      when(mockAPIGatewayClient.createApiKey(createApiKeyRequestCaptor.capture())).thenReturn(CreateApiKeyResponse.builder().id(apiKeyId).build())

      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanKeyRequest])
      when(mockAPIGatewayClient.createUsagePlanKey(createUsagePlanKeyRequestCaptor.capture())).thenReturn(CreateUsagePlanKeyResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(usagePlanName)._1,
        addApplicationHandler.NamedUsagePlans(usagePlanName)._2)
      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create a new Gold API Gateway Usage Plan if the application is new" in new NoMatchingUsagePlan {
      val usagePlanName = "GOLD"

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanRequest])
      when(mockAPIGatewayClient.createUsagePlan(createUsagePlanRequestCaptor.capture())).thenReturn(CreateUsagePlanResponse.builder().id(usagePlanId).build())

      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = ArgumentCaptor.forClass(classOf[CreateApiKeyRequest])
      when(mockAPIGatewayClient.createApiKey(createApiKeyRequestCaptor.capture())).thenReturn(CreateApiKeyResponse.builder().id(apiKeyId).build())

      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanKeyRequest])
      when(mockAPIGatewayClient.createUsagePlanKey(createUsagePlanKeyRequestCaptor.capture())).thenReturn(CreateUsagePlanKeyResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(usagePlanName)._1,
        addApplicationHandler.NamedUsagePlans(usagePlanName)._2)
      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create API Key and UsagePlanKey link if previous calls failed" in new ExistingUsagePlan {
      val usagePlanName = "BRONZE"

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      when(mockAPIGatewayClient.updateUsagePlan(updateUsagePlanRequestCaptor.capture())).thenReturn(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = ArgumentCaptor.forClass(classOf[CreateApiKeyRequest])
      when(mockAPIGatewayClient.createApiKey(createApiKeyRequestCaptor.capture())).thenReturn(CreateApiKeyResponse.builder().id(apiKeyId).build())

      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanKeyRequest])
      when(mockAPIGatewayClient.createUsagePlanKey(createUsagePlanKeyRequestCaptor.capture())).thenReturn(CreateUsagePlanKeyResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 2

      capturedUpdateRequest.patchOperations() should contain only (
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/rateLimit").value(addApplicationHandler.NamedUsagePlans(usagePlanName)._1.toString).build(),
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/burstLimit").value(addApplicationHandler.NamedUsagePlans(usagePlanName)._2.toString).build()
      )

      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create UsagePlanKey link if previous call failed" in new ExistingUnlinkedUsagePlanAndAPIKey {
      val usagePlanName = "BRONZE"

      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlanName, serverToken)))

      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      when(mockAPIGatewayClient.updateUsagePlan(updateUsagePlanRequestCaptor.capture())).thenReturn(UpdateUsagePlanResponse.builder().id(usagePlanId).build())

      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = ArgumentCaptor.forClass(classOf[CreateApiKeyRequest])
      when(mockAPIGatewayClient.createApiKey(createApiKeyRequestCaptor.capture())).thenReturn(CreateApiKeyResponse.builder().id(apiKeyId).build())

      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanKeyRequest])
      when(mockAPIGatewayClient.createUsagePlanKey(createUsagePlanKeyRequestCaptor.capture())).thenReturn(CreateUsagePlanKeyResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedUpdateRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedUpdateRequest.usagePlanId() shouldEqual usagePlanId
      capturedUpdateRequest.patchOperations() should have length 2

      capturedUpdateRequest.patchOperations() should contain only (
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/rateLimit").value(addApplicationHandler.NamedUsagePlans(usagePlanName)._1.toString).build(),
        PatchOperation.builder().op(Op.REPLACE).path("/throttle/burstLimit").value(addApplicationHandler.NamedUsagePlans(usagePlanName)._2.toString).build()
      )

      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "throw exception if the event has no messages" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List())

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](addApplicationHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 0"

      verifyZeroInteractions(mockAPIGatewayClient)
    }

    "throw exception if the event has multiple messages" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(
        List(
          buildAddApplicationRequest(applicationName, "BRONZE", serverToken),
          buildAddApplicationRequest(applicationName, "BRONZE", serverToken)))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](addApplicationHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 2"

      verifyZeroInteractions(mockAPIGatewayClient)
    }
  }

  def createUsagePlanRequestCorrectlyFormatted(argumentCaptor: ArgumentCaptor[CreateUsagePlanRequest], expectedApplicationName: String, expectedRateLimit: Double, expectedBurstLimit: Int): Unit = {
    val capturedRequest: CreateUsagePlanRequest = argumentCaptor.getValue
    capturedRequest.name() shouldEqual expectedApplicationName
    capturedRequest.throttle().rateLimit() shouldEqual expectedRateLimit
    capturedRequest.throttle().burstLimit() shouldEqual expectedBurstLimit
  }

  def createAPIKeyRequestCorrectlyFormatted(argumentCaptor: ArgumentCaptor[CreateApiKeyRequest], expectedApplicationName: String, expectedServerToken: String): Unit = {
    val capturedAPIKeyRequest: CreateApiKeyRequest = argumentCaptor.getValue
    capturedAPIKeyRequest.name() shouldEqual expectedApplicationName
    capturedAPIKeyRequest.value() shouldEqual expectedServerToken
    capturedAPIKeyRequest.enabled() shouldBe true
    capturedAPIKeyRequest.generateDistinctId() shouldBe false
  }

  def createUsagePlanKeyRequestCorrectlyFormatted(argumentCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest], expectedUsagePlanId: String, expectedApiKeyId: String): Unit = {
    val capturedUsagePlanKeyRequest: CreateUsagePlanKeyRequest = argumentCaptor.getValue
    capturedUsagePlanKeyRequest.usagePlanId() shouldEqual expectedUsagePlanId
    capturedUsagePlanKeyRequest.keyId() shouldEqual expectedApiKeyId
    capturedUsagePlanKeyRequest.keyType() shouldEqual "API_KEY"
  }
}
