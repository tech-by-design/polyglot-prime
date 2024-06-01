package org.techbd.service.http.hub.prime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = { "org.techbd" })
@EnableJpaRepositories(basePackages = "org.techbd.udi")
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
