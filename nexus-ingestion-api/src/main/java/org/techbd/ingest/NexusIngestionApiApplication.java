package org.techbd.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = { "org.techbd" })
public class NexusIngestionApiApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NexusIngestionApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(NexusIngestionApiApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("NexusIngestionApiApplication started successfully — TCP listener removed.");
    }
}
