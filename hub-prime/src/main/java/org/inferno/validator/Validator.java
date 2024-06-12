package org.inferno.validator;

import com.google.gson.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.formats.FormatUtilities;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.validation.BaseValidator;
import org.hl7.fhir.validation.BaseValidator.ValidationControl;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.ValidationEngine.ValidationEngineBuilder;
import org.inferno.validator.rest.IgResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class Validator {
  private final ValidationEngine hl7Validator;
  private final FilesystemPackageCacheManager packageManager;
  private final Map<String, NpmPackage> loadedPackages;

  private static String assignedUrlFrom = null;
  private static String vesrionFrom = null;
  
  private static final Logger LOGGER = LoggerFactory.getLogger(Validator.class);

  /**
   * Creates the HL7 Validator to which can then be used for validation.
   *
   * @param igDir A directory containing tarred/gzipped IG packages
   * @throws Exception If the validator cannot be created
   */
  public Validator(String igDir) throws Exception {
    this(igDir, false);
  }

  /**
   * Creates the HL7 Validator to which can then be used for validation.
   *
   * @param igDir A directory containing tarred/gzipped IG packages
   * @param displayIssuesAreWarnings
   *    Toggles whether code display mismatches should be
   *      reported as warnings (true) or errors (false).
   * @throws Exception If the validator cannot be created
   */
  public Validator(String igDir, boolean displayIssuesAreWarnings) throws Exception {
    final String fhirSpecVersion = "4.0";
    final String definitions = VersionUtilities.packageForVersion(fhirSpecVersion)
        + "#" + VersionUtilities.getCurrentVersion(fhirSpecVersion);
    final String txServer = getTxServerUrl();
    final String txLog = null;
    final String fhirVersion = "4.0.1";

    ValidationEngineBuilder engineBuilder =
        new ValidationEngineBuilder().withTxServer(
                                                   txServer,
                                                   txLog,
                                                   FhirPublication.fromCode(fhirVersion),true
                                                   );
    hl7Validator = engineBuilder.fromSource(definitions);

    // The two lines below turn off URL resolution checking in the validator.
    // This eliminates the need to silence these errors elsewhere in Inferno
    // And also keeps contained resources from failing validation based solely on URL errors
    ValidationControl vc = new BaseValidator(hl7Validator.getContext(), null, false)
                             .new ValidationControl(false, IssueSeverity.INFORMATION);
    hl7Validator.getValidationControl().put("Type_Specific_Checks_DT_URL_Resolve", vc);

    // Get all the package gzips in the "igs/package" directory
    File dir = new File(igDir);
    File[] igFiles = dir.listFiles((d, name) -> name.endsWith(".tgz"));
    if (igFiles != null) {
      // sort the files by name to ensure a consistent order -- see File.compareTo(File)
      // https://docs.oracle.com/javase/8/docs/api/java/io/File.html#compareTo-java.io.File-
      Arrays.sort(igFiles);
      for (File igFile : igFiles) {
        hl7Validator
            .getIgLoader()
            .loadIg(
                    hl7Validator.getIgs(),
                    hl7Validator.getBinaries(),
                    igFile.getAbsolutePath(),
                    true
                    );
      }
    }

    hl7Validator.connectToTSServer(txServer, txLog, FhirPublication.fromCode(fhirVersion),true);
    hl7Validator.setDoNative(false);
    hl7Validator.setAnyExtensionsAllowed(true);
    hl7Validator.setDisplayWarnings(displayIssuesAreWarnings);
    hl7Validator.prepare();

    packageManager = new FilesystemPackageCacheManager.Builder().build();
    loadedPackages = new HashMap<>();
  }

  public   String getAssignedUrlFrom() {
    return assignedUrlFrom;
  }

  public static void setAssignedUrlFrom(String assignedUrlFrom) {
    Validator.assignedUrlFrom = assignedUrlFrom;
  }

  public   String getVersionFrom() {
    return vesrionFrom;
  }

  public static void setVersionFrom(String vesrionFrom) {
    Validator.vesrionFrom = vesrionFrom;
  }

  /**
   * Lists the names of resources defined for this version of the validator.
   *
   * @return a sorted list of distinct resource names
   */
  public List<String> getResources() {
    return hl7Validator.getContext().getResourceNames()
        .stream()
        .sorted()
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Lists the StructureDefinitions loaded in the validator.
   *
   * @return a sorted list of distinct structure canonicals
   */
  public List<String> getStructures() {
    List<StructureDefinition> structures =
        hl7Validator
            .getContext()
            .fetchResourcesByType(StructureDefinition.class);
    return structures
        .stream()
        .map(StructureDefinition::getUrl)
        .sorted()
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Validates the given resource against the given list of profiles.
   *
   * @param resource a byte array representation of a FHIR resource
   * @param profiles a list of profile URLs to validate against
   * @return an OperationOutcome resource representing the result of the validation operation
   */
  public OperationOutcome validate(byte[] resource, List<String> profiles) {
    Manager.FhirFormat fmt = FormatUtilities.determineFormat(resource);
    ByteArrayInputStream resourceStream = new ByteArrayInputStream(resource);
    OperationOutcome oo;
    Gson gson = new Gson();
    try {
      // Convert byte[] to String
      String resourceJson = new String(resource, StandardCharsets.UTF_8);
      JsonElement jsonElement = com.google.gson.JsonParser.parseString(resourceJson);
      JsonObject jsonObject = jsonElement.getAsJsonObject();

      if (jsonObject.has("property") && jsonObject.get("property").isJsonObject()) {
        JsonObject propertyObject = jsonObject.getAsJsonObject("property");

        // Convert the object to an array
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(propertyObject);
        jsonObject.add("property", jsonArray);

        // Convert the modified JsonObject back to a string
        resource = gson.toJson(jsonObject).getBytes(StandardCharsets.UTF_8);

        // Proceed with the parsing operation
        Resource resourceObj = FormatUtilities.makeParser(fmt).parse(resource);
        System.out.println("Parsing successful: " + resourceObj);
      } else {
        System.out.println("The JSON structure is not as expected. Skipping parsing.");
      }
    } catch (JsonSyntaxException | IOException e) {
      System.err.println("Error occurred in parsing resource: " + e.getMessage());
      e.printStackTrace();
    }

    try {
      // Validate the resource
      oo = hl7Validator.validate(fmt, resourceStream, profiles);
    } catch (Exception e) {
      // Handle validation exceptions and create an OperationOutcome for errors
      OperationOutcome.IssueSeverity sev = OperationOutcome.IssueSeverity.FATAL;
      OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent(sev, IssueType.STRUCTURE);
      issue.setDiagnostics(e.getMessage());
      issue.setDetails(new CodeableConcept().setText(e.getMessage()));
      issue.addExtension("http://hl7.org/fhir/StructureDefinition/operationoutcome-issue-line", new IntegerType(1));
      issue.addExtension("http://hl7.org/fhir/StructureDefinition/operationoutcome-issue-col", new IntegerType(1));
      issue.addExtension("http://hl7.org/fhir/StructureDefinition/operationoutcome-issue-source", new CodeType("ValidationService"));
      oo = new OperationOutcome();
      oo.addIssue(issue);
    } finally {
      try {
        resourceStream.close();
      } catch (IOException e) {
        LOGGER.warn("Failed to close resource stream", e);
      }
    }

    return oo;
  }

  /**
   * Provides a map of known IGs that can be retrieved and loaded.
   *
   * @return a map containing each known IG ID and its corresponding canonical URL.
   */
  public Map<String, String> getKnownIGs() throws IOException {
    Map<String, String> igs = new HashMap<>();
    // Add known custom IGs
    for (Map.Entry<String, NpmPackage> e : loadedPackages.entrySet()) {
      String id = e.getKey().split("#")[0];
      String canonical = e.getValue().canonical();
      igs.put(id, canonical);
    }
    // Add IGs known to the package manager, replacing any conflicting package IDs
    packageManager.listAllIds(igs);
    return igs;
  }

  /**
   * Load a profile into the validator.
   *
   * @param profile the profile to be loaded
   */
  public void loadProfile(byte[] profile) throws IOException {
    Manager.FhirFormat fmt = FormatUtilities.determineFormat(profile);
    Resource resource = FormatUtilities.makeParser(fmt).parse(profile);
    hl7Validator.getContext().cacheResource(resource);
    if (resource instanceof StructureDefinition) {
      StructureDefinition sd = (StructureDefinition)resource;
      LOGGER.info("Loaded profile from file, url: " + sd.getUrl() + " version: " + sd.getVersion());
      setAssignedUrlFrom(sd.getUrl());
      setVersionFrom(sd.getVersion());
    } else if (resource != null) {
      LOGGER.info("Loaded resource from file but it wasn't a StructureDefinition, it was a "
        + resource.fhirType());
    }
  }

  /**
   * Finds any custom package that fits the given id and (possibly null) version.
   *
   * @param id the ID of the custom package
   * @param version the version of the custom package, or null to return the first match
   * @return a matching custom IG package, or null if no matching package was found
   */
  private NpmPackage findCustomPackage(String id, String version) {
    String idRegex = "^" + id + "#" + (version != null ? version : ".*") + "$";
    for (Map.Entry<String, NpmPackage> entry : loadedPackages.entrySet()) {
      if (entry.getKey().matches(idRegex)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private IgResponse getIg(String id, String version) throws IOException {
    NpmPackage npm = findCustomPackage(id, version);
    // Fallback to packages from packages.fhir.org if no custom packages match
    if (npm == null) {
      npm = packageManager.loadPackage(id, version);
    }
    return IgResponse.fromPackage(npm);
  }

  /**
   * Load an IG into the validator.
   *
   * @param id the package ID of the FHIR IG to be loaded
   * @param version the package version of the FHIR IG to be loaded
   * @return an IgResponse representing the package that was loaded
   */
  public IgResponse loadIg(String id, String version) throws Exception {
    NpmPackage npm = findCustomPackage(id, version);
    // Fallback to packages from packages.fhir.org if no custom packages match
    if (npm == null) {
      hl7Validator
          .getIgLoader()
          .loadIg(
                  hl7Validator.getIgs(),
                  hl7Validator.getBinaries(),
                  id + (version != null ? "#" + version : ""),
                  true
                  );
      npm = packageManager.loadPackage(id, version);
      if (npm != null) {
        LOGGER.info("Loaded IG by identifier: " + npm.id() + "#" + npm.version());
      }
    }
    return IgResponse.fromPackage(npm);
  }

  /**
   * Load a Gzipped IG into the validator.
   *
   * @param content the Gzip-encoded contents of the IG package to be loaded
   * @return an IgResponse representing the package that was loaded
   */
  public IgResponse loadPackage(byte[] content) throws Exception {
    File temp = File.createTempFile("package", ".tgz");
    temp.deleteOnExit();
    try {
      FileUtils.writeByteArrayToFile(temp, content);
      hl7Validator
          .getIgLoader()
          .loadIg(
                  hl7Validator.getIgs(),
                  hl7Validator.getBinaries(),
                  temp.getCanonicalPath(),
                  true
                  );
    } finally {
      temp.delete();
    }
    NpmPackage npm = NpmPackage.fromPackage(new ByteArrayInputStream(content));
    loadedPackages.put(npm.id() + "#" + npm.version(), npm);
    LOGGER.info("Loaded IG from tgz upload: " + npm.id() + "#" + npm.version());
    return IgResponse.fromPackage(npm);
  }

  /**
   * Get a mapping from IG URL to a list of profile URLs supported by the IG.
   *
   * @return a mapping from IG URL to a list of profile URLs supported by the IG.
   */
  public Map<String, List<String>> getProfilesByIg() {
    List<ImplementationGuide> igs = hl7Validator.getContext().allImplementationGuides();
    return igs
        .stream()
        .collect(Collectors.toMap(
            ImplementationGuide::getPackageId,
            ig -> {
              try {
                return getIg(ig.getPackageId(), ig.getVersion()).getProfiles();
              } catch (IOException e) {
                return new ArrayList<>();
              }
            },
            (existing, replacement) -> existing
        ));
  }

  /**
   * Load a profile from a file.
   *
   * @param src the file path
   * @throws IOException if the file fails to load
   */
  public void loadProfileFromFile(String src) throws IOException {
    byte[] profile = loadResourceFromFile(src);
    loadProfile(profile);
  }

  private byte[] loadResourceFromFile(String src) throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    URL file = classLoader.getResource(src);
    return IOUtils.toByteArray(file);
  }

  private String getTxServerUrl() {
    if (disableTxValidation()) {
      return null;
    }

    if (System.getenv("TX_SERVER_URL") != null) {
      return System.getenv("TX_SERVER_URL");
    } else {
      return "http://tx.fhir.org";
    }
  }

  private boolean disableTxValidation() {
    return System.getenv("DISABLE_TX") != null;
  }
}
