package uk.gov.hmrc.apiplatform.upsertapplication

import java.util.UUID

import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{ApiKey, GetApiKeysRequest, GetApiKeysResponse, GetUsagePlanKeysRequest, GetUsagePlanKeysResponse, GetUsagePlansRequest, GetUsagePlansResponse, UsagePlan, UsagePlanKey}
import org.scalatest.mockito.MockitoSugar

import scala.collection.JavaConversions.seqAsJavaList

trait Setup extends MockitoSugar {
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