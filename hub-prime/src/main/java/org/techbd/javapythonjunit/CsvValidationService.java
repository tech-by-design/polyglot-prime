package org.techbd.javapythonjunit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CsvValidationService {

    @Value("${org.techbd.csv.validation.python-script-path}")
    private String pythonScriptPath;

    @Value("${org.techbd.csv.validation.python-executable:python3}")
    String pythonExecutable;

    @Value("${org.techbd.csv.validation.package-path}")
    private String packagePath;

    @Value("${org.techbd.csv.validation.output-path}")
    private String outputPath;

    @Value("${org.techbd.csv.validation.base-path}")
    private String basePath;

    @Getter
    @Setter
    @Value("${org.techbd.csv.validation.file1}")
    private String file1;

    @Getter
    @Setter
    @Value("${org.techbd.csv.validation.file2}")
    private String file2;

    @Getter
    @Setter
    @Value("${org.techbd.csv.validation.file3}")
    private String file3;

    protected ProcessBuilder createProcessBuilder() {
        return new ProcessBuilder();
    }

    public Map<String, Object> validateCsvGroup() throws Exception {
        try {

            // Build command
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(pythonScriptPath);
            command.add(packagePath);
            command.add(file1);
            command.add(file2);
            command.add(file3);
            command.add(outputPath);

            log.info("Executing command: {}", String.join(" ", command));

            ProcessBuilder processBuilder = createProcessBuilder();
            processBuilder.command(command);
            processBuilder.redirectErrorStream(true);

            // Set working directory to the project root
            processBuilder.directory(new File(basePath));

            Process process = processBuilder.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("Python output: {}", line);
                }
            }

            // Read error stream separately
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    log.error("Python error: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMessage = String.format(
                        "Python validation script failed with exit code: %d\nOutput: %s\nError: %s",
                        exitCode, output, errorOutput);
                log.error(errorMessage);
                throw new Exception(errorMessage);
            }

            log.debug("Python script output: {}", output);

            var result = output.toString();
            log.info("Python validation script executed successfully: " + result);
            return Map.of("result", result);

        } catch (Exception e) {
            log.error("Error validating CSV files: {}", e.getMessage(), e.getStackTrace());
            throw new Exception("Failed to validate CSV files", e);
        }
    }
}
