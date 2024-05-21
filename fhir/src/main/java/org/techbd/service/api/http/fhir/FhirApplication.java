package org.techbd.service.api.http.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.techbd.service.api.http")
public class FhirApplication {

	public static void main(String[] args) {
		SpringApplication.run(FhirApplication.class, args);
	}

}
