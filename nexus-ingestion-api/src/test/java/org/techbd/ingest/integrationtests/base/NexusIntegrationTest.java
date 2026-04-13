package org.techbd.ingest.integrationtests.base;
import org.techbd.ingest.NexusIngestionApiApplication;
import java.lang.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = NexusIngestionApiApplication.class
)
@TestPropertySource(properties = {
    "PORT_CONFIG_S3_BUCKET=local-pdr-txd-sbx-temp",
    "PORT_CONFIG_S3_KEY=port-config/list.json",
    "SPRING_PROFILES_ACTIVE=test"
})
@ActiveProfiles("test")
@Testcontainers
public @interface NexusIntegrationTest {
}