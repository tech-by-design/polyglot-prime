package org.techbd.csv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "org.techbd" })
public class CsvApplication {

	public static void main(String[] args) {
		SpringApplication.run(CsvApplication.class, args);
	}

}
