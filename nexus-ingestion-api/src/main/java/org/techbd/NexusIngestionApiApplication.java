package org.techbd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "org.techbd" })
public class NexusIngestionApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(NexusIngestionApiApplication.class, args);
	}

}
