package lab.payments.payment_session;

import com.github.tomakehurst.wiremock.WireMockServer;
import lab.testing.SpringBootIntegrationTest;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

@EnableWireMock(@ConfigureWireMock(name = "dynamodb", baseUrlProperties = "lab.endpoint",
    filesUnderClasspath = "wiremock/dynamodb", resetWireMockServer = false))
abstract class PaymentSessionIntegrationTest extends SpringBootIntegrationTest {
    @InjectWireMock("dynamodb")
    protected WireMockServer wireMock;
}
