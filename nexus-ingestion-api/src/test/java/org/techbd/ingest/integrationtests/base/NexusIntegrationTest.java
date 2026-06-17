package org.techbd.ingest.integrationtests.base;

import org.techbd.ingest.NexusIngestionApiApplication;
import java.lang.annotation.*;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NexusIngestionApiApplication.class)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
                "PORT_CONFIG_S3_BUCKET=local-pdr-txd-sbx-temp",
                "PORT_CONFIG_S3_KEY=port-config/list.json",
                "SPRING_PROFILES_ACTIVE=test",
                "TCP_READ_TIMEOUT_SECONDS=10",
                "TCP_DISPATCHER_PORT=7980"
})
@ActiveProfiles("test")
@Tag("integration")
public @interface NexusIntegrationTest {
}