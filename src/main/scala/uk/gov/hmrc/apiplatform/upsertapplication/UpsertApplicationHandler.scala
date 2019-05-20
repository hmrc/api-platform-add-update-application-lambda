package uk.gov.hmrc.apiplatform.upsertapplication

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.AwsIdRetriever
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.SqsHandler

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class UpsertApplicationHandler(override val apiGatewayClient: ApiGatewayClient, environment: Map[String, String]) extends SqsHandler with AwsIdRetriever {

  // Usage Plan Name -> (Rate Limit, Burst Limit)
  val NamedUsagePlans: Map[String, (Double, Int)] =
    Map(
      "BRONZE" -> (2.5d, 3), // 150 requests/min
      "SILVER" -> (8.4d, 9), // 500 requests/min
      "GOLD" -> (16.7d, 17), // 1000 requests/min
      "PLATINUM" -> (66.7, 67) // 4000 requests/min
    )

  def this() {
    this(awsApiGatewayClient, sys.env)
  }

  override def handleInput(input: SQSEvent, context: Context): Unit = {
    implicit val logger: LambdaLogger = context.getLogger
    if (input.getRecords.size != 1) {
      throw new IllegalArgumentException(s"Invalid number of records: ${input.getRecords.size}")
    }

    val upsertRequest: UpsertApplicationRequest = fromJson[UpsertApplicationRequest](input.getRecords.get(0).getBody)

    val usagePlanId: String = getAwsUsagePlanIdByApplicationName(upsertRequest.applicationName) match {
      case Some(id) => logger.log(s"Usage Plan for Application [${upsertRequest.applicationName}] already exists - updating"); updateApplication(id, upsertRequest)
      case None => logger.log(s"Creating Usage Plan for Application [${upsertRequest.applicationName}]"); createApplication(upsertRequest, logger)
    }

    val apiKeyId: String = getAwsApiKeyIdByApplicationName(upsertRequest.applicationName) match {
      case Some(id) => logger.log(s"API Key for Application [${upsertRequest.applicationName}] already exists"); id
      case None => logger.log(s"Creating API Key for Application [${upsertRequest.applicationName}]"); createAPIKey(upsertRequest)
    }

    if (!usagePlanKeyExists(usagePlanId, apiKeyId)) {
      logger.log(s"Linking Usage Plan and API Key for Application [${upsertRequest.applicationName}]")
      linkUsagePlanToKey(usagePlanId, apiKeyId)
    } else {
      logger.log(s"Usage Plan and API Key for Application [${upsertRequest.applicationName}] already linked")
    }
  }

  private def updateApplication(usagePlanId: String, upsertRequest: UpsertApplicationRequest)(implicit logger: LambdaLogger): String = {
    def usagePlanUpdates(existingRateLimit: Double, existingBurstLimit: Int, requestedUsagePlan: String): Seq[PatchOperation] = {
      val updateOperations: ListBuffer[PatchOperation] = ListBuffer[PatchOperation]()

      val requestedRateLimit: Double = NamedUsagePlans(requestedUsagePlan)._1
      if (requestedRateLimit != existingRateLimit) {
        updateOperations += PatchOperation.builder().op(Op.REPLACE).path("/throttle/rateLimit").value(requestedRateLimit.toString).build()
      }

      val requestedBurstLimit: Int = NamedUsagePlans(requestedUsagePlan)._2
      if (requestedBurstLimit != existingBurstLimit) {
        updateOperations += PatchOperation.builder().op(Op.REPLACE).path("/throttle/burstLimit").value(requestedBurstLimit.toString).build()
      }

      updateOperations
    }

    val existingUsagePlan: GetUsagePlanResponse = apiGatewayClient.getUsagePlan(GetUsagePlanRequest.builder().usagePlanId(usagePlanId).build())
    val patchOperations = usagePlanUpdates(existingUsagePlan.throttle().rateLimit(), existingUsagePlan.throttle().burstLimit(), upsertRequest.usagePlan)

    if (patchOperations.nonEmpty) {
      apiGatewayClient.updateUsagePlan(
        UpdateUsagePlanRequest.builder()
          .usagePlanId(usagePlanId)
          .patchOperations(patchOperations.asJava)
          .build())
    }

    usagePlanId
  }

  private def createApplication(upsertRequest: UpsertApplicationRequest, logger: LambdaLogger): String = {
    val usagePlanRequest =
      CreateUsagePlanRequest.builder()
        .name(upsertRequest.applicationName)
        .throttle(buildThrottleSettings(upsertRequest.usagePlan))
        .apiStages(apiNamesToApiStages(upsertRequest.apiNames, logger).asJava)
        .build()

    val response = apiGatewayClient.createUsagePlan(usagePlanRequest)
    response.id()
  }

  private def apiNamesToApiStages(apiNames: Seq[String], logger: LambdaLogger): Seq[ApiStage] = {
    apiNames map { apiName =>
      getAwsRestApiIdByApiName(apiName, logger) match {
        case Some(apiId) => ApiStage.builder().apiId(apiId).stage("current").build()
        case _ => throw NotFoundException.builder().message(s"API '$apiName' not found").build()
      }
    }
  }

  private def buildThrottleSettings(usagePlanName: String): ThrottleSettings =
    ThrottleSettings.builder()
      .rateLimit(NamedUsagePlans(usagePlanName)._1)
      .burstLimit(NamedUsagePlans(usagePlanName)._2)
      .build()

  private def createAPIKey(upsertRequest: UpsertApplicationRequest): String = {
    val apiKeyRequest =
      CreateApiKeyRequest.builder()
        .name(upsertRequest.applicationName)
        .value(upsertRequest.serverToken)
        .generateDistinctId(false)
        .enabled(true)
        .build()

    val response = apiGatewayClient.createApiKey(apiKeyRequest)
    response.id()
  }

  private def linkUsagePlanToKey(usagePlanId: String, apiKeyId: String): Unit = {
    val createUsagePlanKeyRequest =
      CreateUsagePlanKeyRequest.builder()
        .usagePlanId(usagePlanId)
        .keyId(apiKeyId)
        .keyType("API_KEY")
        .build()

    apiGatewayClient.createUsagePlanKey(createUsagePlanKeyRequest)
  }

  private def usagePlanKeyExists(usagePlanId: String, apiKeyId: String): Boolean = {
    apiGatewayClient.getUsagePlanKeys(GetUsagePlanKeysRequest.builder().usagePlanId(usagePlanId).build()).items().asScala
      .exists(k => k.id == apiKeyId)
  }
}

case class UpsertApplicationRequest(applicationName: String, usagePlan: String, serverToken: String, apiNames: Seq[String])
