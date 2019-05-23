package uk.gov.hmrc.apiplatform.upsertapplication

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, verifyZeroInteractions, when}
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class AddApplicationHandlerSpec extends WordSpecLike with Matchers with JsonMapper {

  "Add Application" should {
    "create a new Bronze API Gateway Usage Plan if the application is new" in new NoMatchingUsagePlan {
      val requestedUsagePlan = "BRONZE"

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = callsToCreateUsagePlanCaptured(CreateUsagePlanResponse.builder().id(usagePlanId).build())
      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = callsToCreateApiKeyCaptured(CreateApiKeyResponse.builder().id(apiKeyId).build())
      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = callsToCreateUsagePlanKeyCaptured()

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        Seq())
      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create a new Silver API Gateway Usage Plan if the application is new" in new NoMatchingUsagePlan {
      val requestedUsagePlan = "SILVER"

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = callsToCreateUsagePlanCaptured(CreateUsagePlanResponse.builder().id(usagePlanId).build())
      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = callsToCreateApiKeyCaptured(CreateApiKeyResponse.builder().id(apiKeyId).build())
      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = callsToCreateUsagePlanKeyCaptured()

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        Seq())
      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create a new Gold API Gateway Usage Plan if the application is new" in new NoMatchingUsagePlan {
      val requestedUsagePlan = "GOLD"

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = callsToCreateUsagePlanCaptured(CreateUsagePlanResponse.builder().id(usagePlanId).build())
      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = callsToCreateApiKeyCaptured(CreateApiKeyResponse.builder().id(apiKeyId).build())
      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = callsToCreateUsagePlanKeyCaptured()

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        Seq())
      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create a new Platinum API Gateway Usage Plan if the application is new" in new NoMatchingUsagePlan {
      val requestedUsagePlan = "PLATINUM"

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = callsToCreateUsagePlanCaptured(CreateUsagePlanResponse.builder().id(usagePlanId).build())
      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = callsToCreateApiKeyCaptured(CreateApiKeyResponse.builder().id(apiKeyId).build())
      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = callsToCreateUsagePlanKeyCaptured()

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        Seq())
      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "add API stages to usage plan when APIs provided in the SQS record" in new NoMatchingUsagePlan {
      val requestedUsagePlan = "BRONZE"
      val apis: Seq[String] = Seq("test--api--1.0", "test--api--2.0")
      val apiIds: Seq[String] = apis.map(apiName => TestAPIs(apiName)._1)

      mocksReturnRestApis(apis)

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] =
        callsToCreateUsagePlanCaptured(CreateUsagePlanResponse.builder().id(usagePlanId).build())

      when(mockAPIGatewayClient.createApiKey(any[CreateApiKeyRequest])).thenReturn(CreateApiKeyResponse.builder().id(apiKeyId).build())
      when(mockAPIGatewayClient.createUsagePlanKey(any[CreateUsagePlanKeyRequest])).thenReturn(CreateUsagePlanKeyResponse.builder().build())

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken, apis), mockContext)

      createUsagePlanRequestCorrectlyFormatted(
        createUsagePlanRequestCaptor,
        applicationName,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1,
        addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2,
        apiIds)
    }

    "throw exception if API does not exist" in new NoMatchingUsagePlan {
      val requestedUsagePlan = "BRONZE"
      val apis: Seq[String] = Seq("test--api--1.0")

      mocksReturnRestApis(Seq.empty)

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] =
        callsToCreateUsagePlanCaptured(CreateUsagePlanResponse.builder().id(usagePlanId).build())

      when(mockAPIGatewayClient.createApiKey(any[CreateApiKeyRequest])).thenReturn(CreateApiKeyResponse.builder().id(apiKeyId).build())
      when(mockAPIGatewayClient.createUsagePlanKey(any[CreateUsagePlanKeyRequest])).thenReturn(CreateUsagePlanKeyResponse.builder().build())

      val exception: NotFoundException =
        intercept[NotFoundException](
          addApplicationHandler.handleInput(
            buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken, apis), mockContext))
      exception.getMessage shouldBe "API 'test--api--1.0' not found"
    }

    "create API Key and UsagePlanKey link if previous calls failed" in new ExistingUsagePlan {
      val requestedUsagePlan = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2

      mocksReturnUsagePlan(currentRateLimit, currentBurstLimit)

      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = callsToCreateApiKeyCaptured(CreateApiKeyResponse.builder().id(apiKeyId).build())
      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = callsToCreateUsagePlanKeyCaptured()

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])

      createAPIKeyRequestCorrectlyFormatted(createApiKeyRequestCaptor, applicationName, serverToken)
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "create UsagePlanKey link if previous call failed" in new ExistingUnlinkedUsagePlanAndAPIKey {
      val requestedUsagePlan = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2

      mocksReturnUsagePlan(currentRateLimit, currentBurstLimit)

      val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = callsToCreateApiKeyCaptured(CreateApiKeyResponse.builder().id(apiKeyId).build())
      val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = callsToCreateUsagePlanKeyCaptured()

      addApplicationHandler handleInput(buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext)

      verify(mockAPIGatewayClient, times(0)).updateUsagePlan(any[UpdateUsagePlanRequest])
      verify(mockAPIGatewayClient, times(0)).createApiKey(any[CreateApiKeyRequest])
      createUsagePlanKeyRequestCorrectlyFormatted(createUsagePlanKeyRequestCaptor, usagePlanId, apiKeyId)
    }

    "throw exception if call to create Usage Plan fails" in new NoMatchingUsagePlan {
      val exceptionToThrow: Exception = AwsServiceException.builder().build()
      when(mockAPIGatewayClient.createUsagePlan(any[CreateUsagePlanRequest])).thenThrow(exceptionToThrow)

      val thrownException: Exception =
        intercept[Exception](
          addApplicationHandler.handleInput(
            buildSQSEventWithSingleRequest(applicationName, "BRONZE", serverToken), mockContext))
      thrownException should be theSameInstanceAs exceptionToThrow
    }

    "throw exception if call to create API Key fails" in new ExistingUsagePlan {
      val requestedUsagePlan = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2

      val exceptionToThrow: Exception = AwsServiceException.builder().build()
      when(mockAPIGatewayClient.createApiKey(any[CreateApiKeyRequest])).thenThrow(exceptionToThrow)

      when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
        .thenReturn(buildMatchingUsagePlanResponse(usagePlanId, applicationName, currentRateLimit, currentBurstLimit))

      val thrownException: Exception =
        intercept[Exception](
          addApplicationHandler.handleInput(
            buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext))
      thrownException should be theSameInstanceAs exceptionToThrow
    }

    "throw exception if call to link Usage Plan and API Key fails" in new ExistingUnlinkedUsagePlanAndAPIKey {
      val requestedUsagePlan = "BRONZE"
      val currentRateLimit: Double = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._1
      val currentBurstLimit: Int = addApplicationHandler.NamedUsagePlans(requestedUsagePlan)._2

      val exceptionToThrow: Exception = AwsServiceException.builder().build()
      when(mockAPIGatewayClient.updateUsagePlan(any[UpdateUsagePlanRequest])).thenReturn(UpdateUsagePlanResponse.builder().id(usagePlanId).build())
      when(mockAPIGatewayClient.createUsagePlanKey(any[CreateUsagePlanKeyRequest])).thenThrow(exceptionToThrow)

      when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
        .thenReturn(buildMatchingUsagePlanResponse(usagePlanId, applicationName, currentRateLimit, currentBurstLimit))

      val thrownException: Exception =
        intercept[Exception](
          addApplicationHandler.handleInput(
            buildSQSEventWithSingleRequest(applicationName, requestedUsagePlan, serverToken), mockContext))
      thrownException should be theSameInstanceAs exceptionToThrow
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

  def createUsagePlanRequestCorrectlyFormatted(argumentCaptor: ArgumentCaptor[CreateUsagePlanRequest],
                                               expectedApplicationName: String,
                                               expectedRateLimit: Double,
                                               expectedBurstLimit: Int,
                                               expectedApiIds: Iterable[String]): Unit = {
    val capturedRequest: CreateUsagePlanRequest = argumentCaptor.getValue
    capturedRequest.name() shouldEqual expectedApplicationName
    capturedRequest.throttle().rateLimit() shouldEqual expectedRateLimit
    capturedRequest.throttle().burstLimit() shouldEqual expectedBurstLimit
    if (expectedApiIds.nonEmpty) {
      capturedRequest.apiStages().asScala.map(_.apiId()) should contain theSameElementsAs expectedApiIds
      capturedRequest.apiStages().asScala.map(_.stage()) should contain only "current"
    }
  }

  def createAPIKeyRequestCorrectlyFormatted(argumentCaptor: ArgumentCaptor[CreateApiKeyRequest],
                                            expectedApplicationName: String,
                                            expectedServerToken: String): Unit = {
    val capturedAPIKeyRequest: CreateApiKeyRequest = argumentCaptor.getValue
    capturedAPIKeyRequest.name() shouldEqual expectedApplicationName
    capturedAPIKeyRequest.value() shouldEqual expectedServerToken
    capturedAPIKeyRequest.enabled() shouldBe true
    capturedAPIKeyRequest.generateDistinctId() shouldBe false
  }

  def createUsagePlanKeyRequestCorrectlyFormatted(argumentCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest],
                                                  expectedUsagePlanId: String,
                                                  expectedApiKeyId: String): Unit = {
    val capturedUsagePlanKeyRequest: CreateUsagePlanKeyRequest = argumentCaptor.getValue
    capturedUsagePlanKeyRequest.usagePlanId() shouldEqual expectedUsagePlanId
    capturedUsagePlanKeyRequest.keyId() shouldEqual expectedApiKeyId
    capturedUsagePlanKeyRequest.keyType() shouldEqual "API_KEY"
  }
}
