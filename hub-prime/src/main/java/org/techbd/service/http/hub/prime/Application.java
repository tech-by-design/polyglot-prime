package org.techbd.service.http.hub.prime;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.techbd.javapythonjunit.CsvValidationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication(scanBasePackages = { "org.techbd" })
@EnableJpaRepositories(basePackages = "org.techbd.udi")
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties
public class Application {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Application.class, args);

        // Get service from Spring context
        CsvValidationService service = context.getBean(CsvValidationService.class);

        try {
            Map<String, Object> result = service.validateCsvGroup();
            log.info("Validation result: {}", result);
        } catch (Exception e) {
            log.error("Error validating CSV files: ", e);
        }
    }

}
