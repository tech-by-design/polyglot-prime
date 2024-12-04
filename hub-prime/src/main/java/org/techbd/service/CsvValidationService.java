
package org.techbd.service;

import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.techbd.service.http.hub.prime.AppConfig;

import lib.aide.vfs.VfsIngressConsumer;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CsvValidationService {

    private static final Logger log = LoggerFactory.getLogger(CsvValidationService.class);
    private final AppConfig appConfig;
    private final VfsCoreService vfsCoreService;
    private final AppConfig.CsvValidation.Validation config;
    private final String inboundFolder;
    private final String ingressHome;

    public CsvValidationService(AppConfig appConfig, VfsCoreService vfsCoreService) {
        this.appConfig = appConfig;
        this.vfsCoreService = vfsCoreService;
        this.config = appConfig.getCsv().validation();
        if (this.config == null) {
            throw new IllegalStateException("CSV validation configuration is null");
        }
        this.inboundFolder = config.inboundPath();
        this.ingressHome = config.ingessHomePath();
    }

    private static final Pattern FILE_PATTERN = Pattern.compile(
            "(DEMOGRAPHIC_DATA|QE_ADMIN_DATA|SCREENING)_(.+)");

    // add more files according to the command
    public void executeZipProcessing() {
        try {
            // Log paths for debugging
            log.info("Inbound Folder Path: {}", inboundFolder);
            log.info("Ingress Home Path: {}", ingressHome);
            // Process ZIP files and get the session ID
            UUID processId = processZipFiles();
            log.info("ZIP files processed with session ID: {}", processId);

            // Construct processed directory path
            String processedDirPath = ingressHome + "/" + processId + "/ingress";

            copyFilesToProcessedDir(processedDirPath);
            createOutputFileInProcessedDir(processedDirPath);
            log.info("Attempting to resolve processed directory: {}", processedDirPath);

            // Get processed files for validation
            FileObject processedDir = vfsCoreService
                    .resolveFile(Paths.get(processedDirPath).toAbsolutePath().toString());

            if (!vfsCoreService.fileExists(processedDir)) {
                log.error("Processed directory does not exist: {}", processedDirPath);
                throw new FileSystemException("Processed directory not found: " + processedDirPath);
            }

            // Collect CSV files for validation
            List<String> csvFiles = collectCsvFiles(processedDir);
            log.info("Found {} CSV files for validation", csvFiles.size());

            if (csvFiles.isEmpty()) {
                log.warn("No CSV files found for validation. Skipping validation.");
                return;
            }

            // Validate CSV files
            Map<String, Object> validationResults = validateFiles(csvFiles);
            // log.info("Validation Results: {}", validationResults);

        } catch (Exception e) {
            log.error("Error in ZIP processing tasklet: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing ZIP files: " + e.getMessage(), e);
        }
    }

    private void createOutputFileInProcessedDir(String processedDirPathStr) throws IOException {
        Path processedDirPath = Paths.get(processedDirPathStr);
        Path outputJsonPath = processedDirPath.resolve("output.json");
        if (Files.notExists(outputJsonPath)) {
            Files.createFile(outputJsonPath);
        }
    }

    public void copyFilesToProcessedDir(String processedDirPathStr) throws IOException {
        Path processedDirPath = Paths.get(processedDirPathStr);
        Path pathToPythonExecutable = Paths.get(appConfig.getCsv().validation().packagePath());
        Path pathToPythonScript = Paths.get(appConfig.getCsv().validation().pythonScriptPath());
        if (Files.notExists(processedDirPath)) {
            Files.createDirectories(processedDirPath);
        }
        Files.copy(pathToPythonExecutable, processedDirPath.resolve(pathToPythonExecutable.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(pathToPythonScript, processedDirPath.resolve(pathToPythonScript.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private UUID processZipFiles() throws FileSystemException, org.apache.commons.vfs2.FileSystemException {

        FileObject inboundFO = vfsCoreService.resolveFile(Paths.get(inboundFolder).toAbsolutePath().toString());
        FileObject ingresshomeFO = vfsCoreService.resolveFile(Paths.get(ingressHome).toAbsolutePath().toString());

        if (!vfsCoreService.fileExists(inboundFO)) {
            log.error("Inbound folder does not exist: {}", inboundFO.getName().getPath());
            throw new FileSystemException("Inbound folder does not exist: " + inboundFO.getName().getPath());
        }
        vfsCoreService.validateAndCreateDirectories(ingresshomeFO);

        VfsIngressConsumer consumer = vfsCoreService.createConsumer(
                inboundFO,
                this::extractGroupId,
                this::isGroupComplete);

        // Important: Capture the returned session UUID and processed file paths
        UUID processId = vfsCoreService.processFiles(consumer, ingresshomeFO);
        return processId;
        // return vfsCoreService.processFiles(consumer, ingresshomeFO);
    }

    private List<String> collectCsvFiles(FileObject processedDir) throws FileSystemException {
        List<String> csvFiles = new ArrayList<>();

        try {
            FileObject[] children = processedDir.getChildren();

            if (children == null) {
                log.warn("No children found in processed directory: {}", processedDir.getName().getPath());
                return csvFiles;
            }

            for (FileObject child : children) {
                // Enhanced null and extension checking
                if (child != null
                        && child.getName() != null
                        && "csv".equalsIgnoreCase(child.getName().getExtension())) {
                    log.info("Found CSV file: {}", child.getName().getPath());
                    csvFiles.add(child.getName().getPath());
                }
            }

            if (csvFiles.isEmpty()) {
                log.warn("No CSV files found in directory: {}", processedDir.getName().getPath());
            }
        } catch (org.apache.commons.vfs2.FileSystemException e) {
            log.error("Error collecting CSV files from directory {}: {}",
                    processedDir.getName().getPath(), e.getMessage(), e);
        }

        return csvFiles;
    }

    private Map<String, Object> validateFiles(List<String> csvFiles) {
        Map<String, Object> validationResults = new HashMap<>();

        // Safety check for null or empty files list
        if (csvFiles == null || csvFiles.isEmpty()) {
            log.warn("No CSV files provided for validation when  csvFiles == null ");
            validationResults.put("status", "NO_FILES");
            validationResults.put("message", "No CSV files found for validation");
            return validationResults;
        }

        // Group files by test case number
        Map<String, List<String>> groupedFiles = csvFiles.stream()
                .collect(Collectors.groupingBy(filePath -> {
                    // Extract test case number from file path
                    String fileName = Paths.get(filePath).getFileName().toString();
                    // Extract the testcase number using regex
                    // please change the grouping logic:
                    // To-do:change grouping logic
                    Pattern pattern = Pattern.compile(".*-testcase(\\d+)\\.csv$");
                    var matcher = pattern.matcher(fileName);
                    if (matcher.find()) {
                        return matcher.group(1); // Returns the test case number
                    }
                    return "unknown";
                }));

        // Process each group together
        for (Map.Entry<String, List<String>> entry : groupedFiles.entrySet()) {
            String testCaseNum = entry.getKey();
            List<String> group = entry.getValue();

            try {
                log.debug("Starting CSV validation for test case {}: {}", testCaseNum, group);
                Map<String, Object> groupResults = validateCsvGroup(group);
                validationResults.put("testcase_" + testCaseNum, groupResults);
                log.debug("Validation results for test case {}: {}", testCaseNum, groupResults);
            } catch (Exception e) {
                log.error("Error validating CSV files for test case {}: {}", testCaseNum, e.getMessage(), e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("validationError", e.getMessage());
                errorResult.put("status", "FAILED");
                validationResults.put("testcase_" + testCaseNum, errorResult);
            }
        }

        return validationResults;
    }

    public Map<String, Object> validateCsvGroup(List<String> filePaths) throws Exception {
        try {
            var config = appConfig.getCsv().validation();
            if (config == null) {
                throw new IllegalStateException("CSV validation configuration is null");
            }

            // Enhanced validation input
            if (filePaths == null || filePaths.isEmpty()) {
                log.error("No files provided for validation");
                throw new IllegalArgumentException("No files provided for validation");
            }

            // Ensure the files exist and are valid using VFS before running the validation
            List<FileObject> fileObjects = new ArrayList<>();
            for (String filePath : filePaths) {
                log.info("Validating file: {}", filePath);
                FileObject file = vfsCoreService.resolveFile(filePath);
                if (!vfsCoreService.fileExists(file)) {
                    log.error("File not found: {}", filePath);
                    throw new FileNotFoundException("File not found: " + filePath);
                }
                fileObjects.add(file);
            }

            // Validate and create directories
            vfsCoreService.validateAndCreateDirectories(fileObjects.toArray(new FileObject[0]));

            // Build command to run Python script
            List<String> command = buildValidationCommand(config, filePaths);

            log.info("Executing validation command: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Capture and handle output/error streams
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    log.info("argument : " + line);
                    output.append(line).append("\n");
                }
            }

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python script execution failed. Exit code: {}, Error: {}",
                        exitCode, errorOutput.toString());
                throw new IOException("Python script execution failed with exit code " +
                        exitCode + ": " + errorOutput.toString());
            }

            // Return parsed validation results
            return parseValidationResults(output.toString());

        } catch (IOException | InterruptedException e) {
            log.error("Error during CSV validation: {}", e.getMessage(), e);
            throw new RuntimeException("Error during CSV validation", e);
        }
    }

    private List<String> buildValidationCommand(AppConfig.CsvValidation.Validation config, List<String> filePaths) {
        List<String> command = new ArrayList<>();
        var pythonScriptPath = "";
        var packagePath = "";
        var outputJsonPath="";
        if (!CollectionUtils.isEmpty(filePaths)) {
            Path path = Paths.get(filePaths.get(0));
            Path parentPath = path.getParent();
            pythonScriptPath = parentPath.resolve("validate-nyher-fhir-ig-equivalent-j.py").toString();
            packagePath = parentPath.resolve("datapackage-nyher-fhir-ig-equivalent.json").toString();
            outputJsonPath = parentPath.resolve("datapackage-nyher-fhir-ig-equivalent.json").toString();
        }
        command.add(config.pythonExecutable());
        command.add(pythonScriptPath);
        command.add(packagePath);

        // Add file paths to the command
        command.addAll(filePaths);

        // Pad with empty strings if fewer than 7 files
        while (command.size() < 10) { // 1 (python) + 1 (script) + 1 (package) + 7 (files) //TODO CHECK IF THIS IS
                                      // NEEDED
            command.add("");
        }

        // Add output path
        command.add(outputJsonPath);

        return command;
    }

    private Map<String, Object> parseValidationResults(String output) {
        // Placeholder for parsing validation results
        log.info("Validation output: {}", output);
        Map<String, Object> results = new HashMap<>();
        results.put("raw_output", output);
        return results;
    }

    private String extractGroupId(FileObject file) {
        String fileName = file.getName().getBaseName();
        var matcher = FILE_PATTERN.matcher(fileName);
        return matcher.matches() ? matcher.group(2) : null;
    }

    private boolean isGroupComplete(VfsIngressConsumer.IngressGroup group) {
        if (group == null || group.groupedEntries().isEmpty()) {
            return false;
        }

        boolean hasDemographic = false;
        boolean hasQeAdmin = false;
        boolean hasScreening = false;
        // please add other files also according to command

        for (VfsIngressConsumer.IngressIndividual entry : group.groupedEntries()) {
            String fileName = entry.entry().getName().getBaseName();
            if (fileName.startsWith("DEMOGRAPHIC_DATA")) {
                hasDemographic = true;
            } else if (fileName.startsWith("QE_ADMIN_DATA")) {
                hasQeAdmin = true;
            } else if (fileName.startsWith("SCREENING")) {
                hasScreening = true;
            }
        }

        return hasDemographic && hasQeAdmin && hasScreening;
        // please add the other files according to the command
    }
}
