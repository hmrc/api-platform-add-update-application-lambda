package uk.gov.hmrc.apiplatform.upsertapplication

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.{CreateUsagePlanRequest, CreateUsagePlanResponse, GetRestApisResponse, GetUsagePlansRequest, GetUsagePlansResponse, RestApi, UsagePlan}
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper
import org.mockito.ArgumentMatchers.any

import scala.collection.JavaConversions._

class AddApplicationHandlerSpec extends WordSpecLike with Matchers with MockitoSugar with JsonMapper {

  trait Setup {
    def buildAddApplicationRequest(applicationName: String, usagePlan: String): SQSMessage = {
      val message = new SQSMessage()
      message.setBody(s"""{"applicationName": "$applicationName", "usagePlan": "$usagePlan"}""")

      message
    }

    def buildNonMatchingUsagePlansResponse(count: Int): GetUsagePlansResponse = {
      val items: Seq[UsagePlan] = (1 to count).map(c => UsagePlan.builder().id(s"$c").name(s"Item $c").build())

      GetUsagePlansResponse.builder()
        .items(seqAsJavaList(items))
        .build()
    }

    val usagePlanId: String = UUID.randomUUID().toString
    val applicationName = "test-application"

    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val mockContext: Context = mock[Context]
    when(mockContext.getLogger).thenReturn(mock[LambdaLogger])

    val environment: Map[String, String] = Map()

    when(mockAPIGatewayClient.getUsagePlans(any[GetUsagePlansRequest])).thenReturn(buildNonMatchingUsagePlansResponse(3))

    val addApplicationHandler = new UpsertApplicationHandler(mockAPIGatewayClient, environment)
  }

  "Add Application" should {
    "create a new Bronze API Gateway Usage Plan if the application is new" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, "BRONZE")))

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanRequest])
      when(mockAPIGatewayClient.createUsagePlan(createUsagePlanRequestCaptor.capture())).thenReturn(CreateUsagePlanResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedRequest: CreateUsagePlanRequest = createUsagePlanRequestCaptor.getValue
      capturedRequest.name() shouldEqual applicationName
      capturedRequest.throttle().rateLimit() shouldEqual addApplicationHandler.NamedUsagePlans("BRONZE")._1
      capturedRequest.throttle().burstLimit() shouldEqual addApplicationHandler.NamedUsagePlans("BRONZE")._2
    }

    "create a new Silver API Gateway Usage Plan if the application is new" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, "SILVER")))

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanRequest])
      when(mockAPIGatewayClient.createUsagePlan(createUsagePlanRequestCaptor.capture())).thenReturn(CreateUsagePlanResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedRequest: CreateUsagePlanRequest = createUsagePlanRequestCaptor.getValue
      capturedRequest.name() shouldEqual applicationName
      capturedRequest.throttle().rateLimit() shouldEqual addApplicationHandler.NamedUsagePlans("SILVER")._1
      capturedRequest.throttle().burstLimit() shouldEqual addApplicationHandler.NamedUsagePlans("SILVER")._2
    }

    "create a new Gold API Gateway Usage Plan if the application is new" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, "GOLD")))

      val createUsagePlanRequestCaptor: ArgumentCaptor[CreateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[CreateUsagePlanRequest])
      when(mockAPIGatewayClient.createUsagePlan(createUsagePlanRequestCaptor.capture())).thenReturn(CreateUsagePlanResponse.builder().build())

      addApplicationHandler handleInput(sqsEvent, mockContext)

      val capturedRequest: CreateUsagePlanRequest = createUsagePlanRequestCaptor.getValue
      capturedRequest.name() shouldEqual applicationName
      capturedRequest.throttle().rateLimit() shouldEqual addApplicationHandler.NamedUsagePlans("GOLD")._1
      capturedRequest.throttle().burstLimit() shouldEqual addApplicationHandler.NamedUsagePlans("GOLD")._2
    }

    "throw exception if the event has no messages" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List())

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](addApplicationHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 0"
    }

    "throw exception if the event has multiple messages" in new Setup {
      val sqsEvent = new SQSEvent()
      sqsEvent.setRecords(List(buildAddApplicationRequest(applicationName, "BRONZE"), buildAddApplicationRequest(applicationName, "BRONZE")))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](addApplicationHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 2"
    }
  }
}
