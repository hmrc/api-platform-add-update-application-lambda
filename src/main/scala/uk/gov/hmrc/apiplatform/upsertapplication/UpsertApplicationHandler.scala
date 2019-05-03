package uk.gov.hmrc.apiplatform.upsertapplication

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{CreateUsagePlanRequest, ThrottleSettings}
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.AwsIdRetriever
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.SqsHandler

class UpsertApplicationHandler(override val apiGatewayClient: ApiGatewayClient, environment: Map[String, String]) extends SqsHandler with AwsIdRetriever {

  // Usage Plan Name -> (Rate Limit, Burst Limit)
  val NamedUsagePlans: Map[String, (Double, Int)] =
    Map(
      "BRONZE" -> (10d, 100),
      "SILVER" -> (100d, 1000),
      "GOLD" -> (1000d, 10000)
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

    getAwsUsagePlanIdByApplicationName(upsertRequest.applicationName) match {
      case Some(usagePlanId) => updateApplication(usagePlanId, upsertRequest)
      case None => createApplication(upsertRequest)
    }
  }

  def updateApplication(usagePlanId: String, upsertRequest: UpsertApplicationRequest): Unit = ???

  def createApplication(upsertRequest: UpsertApplicationRequest): Unit = {
    def usagePlanRequest =
      CreateUsagePlanRequest.builder()
        .name(upsertRequest.applicationName)
        .throttle(buildThrottleSettings(upsertRequest.usagePlan))
        .build()

    apiGatewayClient.createUsagePlan(usagePlanRequest)
  }

  def buildThrottleSettings(usagePlanName: String) =
    ThrottleSettings.builder()
      .rateLimit(NamedUsagePlans(usagePlanName)._1)
      .burstLimit(NamedUsagePlans(usagePlanName)._2)
      .build()

}


case class UpsertApplicationRequest(applicationName: String, usagePlan: String)


