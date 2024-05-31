package org.techbd.service.api.http.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "org.techbd.service.api.http")
@EnableJpaRepositories(basePackages = "org.techbd.service.api.http.fhir.repository")
public class FhirApplication {

	public static void main(String[] args) {
		SpringApplication.run(FhirApplication.class, args);
	}

}
