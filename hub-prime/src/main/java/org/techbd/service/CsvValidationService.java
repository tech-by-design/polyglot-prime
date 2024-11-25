package org.techbd.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.service.http.hub.prime.AppConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CsvValidationService {

    private static final Logger log = LoggerFactory.getLogger(CsvValidationService.class);
    private final AppConfig appConfig;

    @PostConstruct
    public void init() {
        log.info("CSV Configuration loaded: {}", appConfig.getCsv());
    }

    protected ProcessBuilder createProcessBuilder() {
        return new ProcessBuilder();
    }

    public Map<String, Object> validateCsvGroup() throws Exception {
        try {
            var config = appConfig.getCsv().validation();
            if (config == null) {
                throw new IllegalStateException("CSV validation configuration is null");
            }

            // Build the command
            List<String> command = new ArrayList<>();
            command.add(config.pythonExecutable());
            command.add(config.pythonScriptPath());
            command.add(config.packagePath());
            command.add(config.file1());
            command.add(config.file2());
            command.add(config.file3());
            command.add(config.outputPath());

            log.info("Executing command: {}", String.join(" ", command));

            ProcessBuilder processBuilder = createProcessBuilder();
            processBuilder.command(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Read output stream
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("Python output: {}", line);
                }
            }

            // Read error stream
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    log.error("Python error: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMessage = String.format(
                        "Python validation script failed with exit code: %d%nOutput: %s%nError: %s",
                        exitCode, output, errorOutput);
                log.error(errorMessage);
                throw new Exception(errorMessage);
            }

            String result = output.toString();
            log.info("Python validation script executed successfully: {}", result);

            return Map.of("result", result);

        } catch (Exception e) {
            log.error("Error validating CSV files: {}", e.getMessage(), e);
            throw new Exception("Failed to validate CSV files", e);
        }
    }
}

