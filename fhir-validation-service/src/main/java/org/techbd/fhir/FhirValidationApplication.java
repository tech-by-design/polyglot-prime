package org.techbd.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "org.techbd" })
public class FhirValidationApplication {

	public static void main(String[] args) {
		SpringApplication.run(FhirValidationApplication.class, args);
	}

}
