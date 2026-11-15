package lab.testing;

import java.time.Duration;

import lab.common.Dynamo;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.http.server.LocalTestWebServer;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

@Tag("compose")
@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"lab.table=lab_test_${random.uuid}", "lab.workers-enabled=false"})
@Import(SpringBootIntegrationTest.TestResourcesConfiguration.class)
public abstract class SpringBootIntegrationTest {
    @LocalServerPort
    protected int port;
    @Autowired
    protected RestTestClient client;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestResourcesConfiguration {
        @Bean
        RestTestClient restTestClient(WebApplicationContext context) {
            var requestFactory = new JdkClientHttpRequestFactory();
            requestFactory.setReadTimeout(Duration.ofSeconds(12));
            return RestTestClient.bindToServer(requestFactory)
                                 .uriBuilderFactory(LocalTestWebServer.obtain(context).uriBuilderFactory()).build();
        }

        @Bean
        DisposableBean testTableCleanup(Dynamo dynamo) {
            if (!dynamo.table.startsWith("lab_test_")) {
                throw new IllegalStateException("Integration test cleanup requires an isolated test table");
            }
            return () -> dynamo.client.deleteTable(request -> request.tableName(dynamo.table));
        }
    }
}
