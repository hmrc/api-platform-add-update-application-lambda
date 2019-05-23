package uk.gov.hmrc.apiplatform.upsertapplication

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._

import scala.collection.JavaConversions.seqAsJavaList

trait Setup extends MockitoSugar {
  val TestAPIs: Map[String, (String, String)] =
    Map(
      "test--api--1.0" -> (UUID.randomUUID().toString, "current"),
      "test--api--2.0" -> (UUID.randomUUID().toString, "current")
    )

  def asApiStage(apiName: String): ApiStage = ApiStage.builder().apiId(TestAPIs(apiName)._1).stage(TestAPIs(apiName)._2).build()
  def asRestApi(apiName: String): RestApi = RestApi.builder().id(TestAPIs(apiName)._1).name(apiName).build()
  def asApiStageString(apiName: String) = s"${TestAPIs(apiName)._1}:${TestAPIs(apiName)._2}"

  def buildSQSEventWithSingleRequest(applicationName: String, usagePlan: String, serverToken: String, apiNames: Iterable[String] = Seq()): SQSEvent = {
    val sqsEvent = new SQSEvent()
    sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, usagePlan, serverToken, apiNames)))
    sqsEvent
  }

  def buildAddApplicationRequest(applicationName: String, usagePlan: String, serverToken: String, apiNames: Iterable[String] = Seq()): SQSMessage = {
    val message = new SQSMessage()
    message.setBody(s"""{"applicationName": "$applicationName", "usagePlan": "$usagePlan", "serverToken": "$serverToken", "apiNames": [${apiNames.map(n => s""""$n"""").mkString(",")}]}""")
    message
  }

  def buildNonMatchingUsagePlansResponse(count: Int): GetUsagePlansResponse = {
    val items: Seq[UsagePlan] = (1 to count).map(c => UsagePlan.builder().id(s"$c").name(s"Item $c").build())

    GetUsagePlansResponse.builder()
      .items(seqAsJavaList(items))
      .build()
  }

  def buildMatchingUsagePlansResponse(matchingId: String, matchingName: String): GetUsagePlansResponse =
    GetUsagePlansResponse.builder()
      .items(UsagePlan.builder().id(matchingId).name(matchingName).build())
      .build()


  def buildMatchingAPIKeysResponse(matchingAPIKeyId: String, matchingApplicationName: String, matchingAPIKey: String): GetApiKeysResponse =
    GetApiKeysResponse.builder()
      .items(
          ApiKey.builder()
            .id(matchingAPIKeyId)
            .name(matchingApplicationName)
            .value(matchingAPIKey)
            .build())
      .build()

  def buildMatchingUsagePlanKeysResponse(matchingUsagePlanId: String, matchingAPIKeyId: String): GetUsagePlanKeysResponse =
    GetUsagePlanKeysResponse.builder()
      .items(
        UsagePlanKey.builder()
          .id(matchingAPIKeyId)
          .value(matchingUsagePlanId)
          .build())
      .build()

  def buildMatchingUsagePlanResponse(id: String, name: String, rateLimit: Double, burstLimit: Int, apiStages: Option[Seq[ApiStage]] = None): GetUsagePlanResponse =
    GetUsagePlanResponse.builder()
      .id(id)
      .name(name)
      .throttle(
        ThrottleSettings.builder()
          .rateLimit(rateLimit)
          .burstLimit(burstLimit)
          .build())
      .apiStages(apiStages.getOrElse(Seq.empty))
      .build()

  def mocksReturnRestApis(apiNames: Seq[String]): OngoingStubbing[GetRestApisResponse] =
    when(mockAPIGatewayClient.getRestApis(any[GetRestApisRequest]))
      .thenReturn(
        GetRestApisResponse.builder()
          .items(apiNames.map(asRestApi))
          .build())

  def mocksReturnUsagePlan(rateLimit: Double, burstLimit: Int, subscriptions: Seq[String] = Seq()): OngoingStubbing[GetUsagePlanResponse] =
    when(mockAPIGatewayClient.getUsagePlan(any[GetUsagePlanRequest]))
      .thenReturn(
        buildMatchingUsagePlanResponse(
          usagePlanId,
          applicationName,
          rateLimit,
          burstLimit,
          Some(subscriptions.map(asApiStage))))

  def callsToCreateUsagePlanCaptured(response: CreateUsagePlanResponse): ArgumentCaptor[CreateUsagePlanRequest] = {
    val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanRequest])
    when(mockAPIGatewayClient.createUsagePlan(createUsagePlanRequestCaptor.capture())).thenReturn(response)

    createUsagePlanRequestCaptor
  }

  def callsToUpdateUsagePlanCaptured(response: UpdateUsagePlanResponse): ArgumentCaptor[UpdateUsagePlanRequest] = {
    val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
    when(mockAPIGatewayClient.updateUsagePlan(updateUsagePlanRequestCaptor.capture())).thenReturn(response)

    updateUsagePlanRequestCaptor
  }

  def callsToCreateApiKeyCaptured(response: CreateApiKeyResponse): ArgumentCaptor[CreateApiKeyRequest] = {
    val createApiKeyRequestCaptor: ArgumentCaptor[CreateApiKeyRequest] = ArgumentCaptor.forClass(classOf[CreateApiKeyRequest])
    when(mockAPIGatewayClient.createApiKey(createApiKeyRequestCaptor.capture())).thenReturn(response)

    createApiKeyRequestCaptor
  }

  def callsToCreateUsagePlanKeyCaptured(): ArgumentCaptor[CreateUsagePlanKeyRequest] = {
    val createUsagePlanKeyRequestCaptor: ArgumentCaptor[CreateUsagePlanKeyRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanKeyRequest])
    when(mockAPIGatewayClient.createUsagePlanKey(createUsagePlanKeyRequestCaptor.capture())).thenReturn(CreateUsagePlanKeyResponse.builder().build())

    createUsagePlanKeyRequestCaptor
  }

  val usagePlanId: String = UUID.randomUUID().toString
  val apiKeyId: String = UUID.randomUUID().toString

  val applicationName = "test-application"
  val serverToken = "foo-bar-baz"

  val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
  val mockContext: Context = mock[Context]
  when(mockContext.getLogger).thenReturn(mock[LambdaLogger])

  val environment: Map[String, String] = Map()

  val addApplicationHandler = new UpsertApplicationHandler(mockAPIGatewayClient, environment)
}

trait NoMatchingUsagePlan extends Setup {
  when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildNonMatchingUsagePlansResponse(3))
  when(mockAPIGatewayClient.getApiKeys(any[GetApiKeysRequest])).thenReturn(GetApiKeysResponse.builder().build())
  when(mockAPIGatewayClient.getUsagePlanKeys(any[GetUsagePlanKeysRequest])).thenReturn(GetUsagePlanKeysResponse.builder().build())
}

trait ExistingUsagePlan extends Setup {
  when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildMatchingUsagePlansResponse(usagePlanId, applicationName))
  when(mockAPIGatewayClient.getApiKeys(any[GetApiKeysRequest])).thenReturn(GetApiKeysResponse.builder().build())
  when(mockAPIGatewayClient.getUsagePlanKeys(any[GetUsagePlanKeysRequest])).thenReturn(GetUsagePlanKeysResponse.builder().build())
}

trait ExistingUnlinkedUsagePlanAndAPIKey extends Setup {
  when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildMatchingUsagePlansResponse(usagePlanId, applicationName))
  when(mockAPIGatewayClient.getApiKeys(any[GetApiKeysRequest])).thenReturn(buildMatchingAPIKeysResponse(apiKeyId, applicationName, serverToken))
  when(mockAPIGatewayClient.getUsagePlanKeys(any[GetUsagePlanKeysRequest])).thenReturn(GetUsagePlanKeysResponse.builder().build())
}

trait ExistingLinkedUsagePlanAndAPIKey extends Setup {
  when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildMatchingUsagePlansResponse(usagePlanId, applicationName))
  when(mockAPIGatewayClient.getApiKeys(any[GetApiKeysRequest])).thenReturn(buildMatchingAPIKeysResponse(apiKeyId, applicationName, serverToken))
  when(mockAPIGatewayClient.getUsagePlanKeys(any[GetUsagePlanKeysRequest])).thenReturn(buildMatchingUsagePlanKeysResponse(usagePlanId, apiKeyId))
}