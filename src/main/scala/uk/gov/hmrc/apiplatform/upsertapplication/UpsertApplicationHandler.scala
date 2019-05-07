package uk.gov.hmrc.apiplatform.upsertapplication

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{CreateApiKeyRequest, CreateUsagePlanKeyRequest, CreateUsagePlanRequest, ThrottleSettings}
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.AwsIdRetriever
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.SqsHandler

class UpsertApplicationHandler(override val apiGatewayClient: ApiGatewayClient, environment: Map[String, String]) extends SqsHandler with AwsIdRetriever {

  // Usage Plan Name -> (Rate Limit, Burst Limit)
  val NamedUsagePlans: Map[String, (Double, Int)] =
    Map(
      "BRONZE" -> (2.5d, 3), // 150 requests/min
      "SILVER" -> (8.4d, 9), // 500 requests/min
      "GOLD" -> (16.7d, 17) // 1000 requests/min
    )

  def this() {
    this(awsApiGatewayClient, sys.env)
  }

  override def handleInput(input: SQSEvent, context: Context): Unit = {
    val logger: LambdaLogger = context.getLogger
    if (input.getRecords.size != 1) {
      throw new IllegalArgumentException(s"Invalid number of records: ${input.getRecords.size}")
    }

    val upsertRequest: UpsertApplicationRequest = fromJson[UpsertApplicationRequest](input.getRecords.get(0).getBody)

    val usagePlanId: String = getAwsUsagePlanIdByApplicationName(upsertRequest.applicationName) match {
      case Some(id) => updateApplication(id, upsertRequest)
      case None => createApplication(upsertRequest)
    }

    val apiKeyId: String = getAwsApiKeyIdByApplicationName(upsertRequest.applicationName) match {
      case Some(id) => logger.log("API Key already exists"); id
      case None => createAPIKey(upsertRequest)
    }

    linkUsagePlanToKey(usagePlanId, apiKeyId)
  }

  def updateApplication(usagePlanId: String, upsertRequest: UpsertApplicationRequest): String = ???

  def createApplication(upsertRequest: UpsertApplicationRequest): String = {
    def usagePlanRequest =
      CreateUsagePlanRequest.builder()
        .name(upsertRequest.applicationName)
        .throttle(buildThrottleSettings(upsertRequest.usagePlan))
        .build()

    val response = apiGatewayClient.createUsagePlan(usagePlanRequest)
    response.id()
  }

  def buildThrottleSettings(usagePlanName: String): ThrottleSettings =
    ThrottleSettings.builder()
      .rateLimit(NamedUsagePlans(usagePlanName)._1)
      .burstLimit(NamedUsagePlans(usagePlanName)._2)
      .build()

  def createAPIKey(upsertRequest: UpsertApplicationRequest): String = {
    def apiKeyRequest =
      CreateApiKeyRequest.builder()
        .name(upsertRequest.applicationName)
        .value(upsertRequest.serverToken)
        .generateDistinctId(false)
        .enabled(true)
        .build()

    val response = apiGatewayClient.createApiKey(apiKeyRequest)
    response.id()
  }

  def linkUsagePlanToKey(usagePlanId: String, apiKeyId: String): Unit = {
    val createUsagePlanKeyRequest =
      CreateUsagePlanKeyRequest.builder()
        .usagePlanId(usagePlanId)
        .keyId(apiKeyId)
        .keyType("API_KEY")
        .build()

    apiGatewayClient.createUsagePlanKey(createUsagePlanKeyRequest)
  }

}

case class UpsertApplicationRequest(applicationName: String, usagePlan: String, serverToken: String)


