package uk.gov.hmrc.apiplatform.upsertapplication

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanRequest
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.AwsIdRetriever
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.SqsHandler

class UpsertApplicationHandler(override val apiGatewayClient: ApiGatewayClient, environment: Map[String, String]) extends SqsHandler with AwsIdRetriever {

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
    val usagePlanRequest = CreateUsagePlanRequest.builder().name(upsertRequest.applicationName).build()
    apiGatewayClient.createUsagePlan(usagePlanRequest)
  }

}

case class UpsertApplicationRequest(applicationName: String, usagePlan: String)
