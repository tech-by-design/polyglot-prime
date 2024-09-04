# JUnit 5 AssertJ Unit Tests

This directory contains JUnit 5 AssertJ unit tests for various user-facing
services within the project, including but not limited to:

- Web application services
- FHIR endpoints
- FHIR validation
- Other related services

These tests are critical to ensuring the quality and reliability of the services
we provide to our users.

## Java 21 Requirement

Java 21 or above is required to run these tests. If you're setting up a local
development environment, you can easily install Java 21 using
[SDKMAN!](https://sdkman.io/).

## SHIN-NY FHIR Implementation Guide Publication Issues

Within this directory, you will find specific tests aimed at verifying
compliance with the SHIN-NY FHIR Implementation Guide (IG). The primary test
class for this purpose is located in
[org/techbd/orchestrate/fhir/IgPublicationIssuesTest.java](org/techbd/orchestrate/fhir/IgPublicationIssuesTest.java);

#### More about the Implementation Guide
The FHIR v4 Implementation Guide you provided primarily defines the "SHINNY Bundle Profile," which is an extension of the base FHIR Bundle. This profile introduces additional constraints and rules for working with bundles in specific healthcare contexts. Here’s a summary of its key contents:

**StructureDefinition:**
The SHINNY Bundle Profile extends the base FHIR Bundle resource, imposing additional constraints to ensure interoperability and adherence to specific healthcare use cases.
The profile is in the draft stage and is identified by the URL `https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json`

**Constraints:**
The profile defines several constraints on the Bundle resource, such as ensuring that the total element is only present when the bundle is a search set or history, and specific constraints on the use of entry.request and entry.response elements depending on the type of bundle.
The profile also includes custom constraints like ensuring a relationship exists between a Patient and an Encounter or Location within the bundle.

**Mappings:**
The SHINNY Bundle Profile includes mappings to HL7 v2, HL7 v3 (RIM), CDA (R2), and the FiveWs pattern.
Additional Extensions and Elements:

The profile introduces elements like Bundle.meta, which includes metadata about the resource, and Bundle.link, which provides links related to the bundle.
The profile also supports extensions and modifier extensions that can represent additional implementation-specific information.

**Usage:**
This guide is aimed at ensuring that the resources within the bundle adhere to specific rules and relationships, particularly in environments where specific organizational or encounter-related relationships must be maintained.
The guide is detailed and provides explicit rules for managing and structuring healthcare data within bundles, making it crucial for developers and implementers working with FHIR in healthcare applications.

### IG Source

The Implementation Guide being tested is defined as a constant `FHIR_PROFILE_URL` within class
[org/techbd/orchestrate/fhir/IgPublicationIssuesTest.java](org/techbd/orchestrate/fhir/IgPublicationIssuesTest.java);

If you want to test a different version, update it there before running the
tests.

### Sandbox or CI/CD DNS `resolv.conf` to mimic `shinny.org` publish location

To run tests against a development version of the SHIN-NY FHIR Implementation
Guide (IG) as if it were published at the canonical location
(`https://shinny.org/ImplementationGuide/HRSN`), you have two options:

1. **Use `sed` or `grep` to Replace Canonical References**:
   - If you prefer not to modify DNS settings, you can use tools like `sed` or
     `grep` to replace all references to the canonical location in the source
     JSON files with your local or CI/CD environment's equivalent.
   - This method involves preprocessing the test fixtures before running the
     tests.
   - **Example**:
     ```bash
     sed -i 's|https://shinny.org/ImplementationGuide/HRSN|http://localhost:8000/ImplementationGuide/HRSN|g' path/to/your/source.json
     ```

2. **Modify `resolv.conf` to Redirect DNS**:
   - If you prefer not to modify the IG to do testing (since a modification to
     the IG is then not testing the final version) then you can configure your
     local or CI/CD machine's `resolv.conf` file to redirect requests for
     `shinny.org` to a local web server hosting your development version of the
     IG.
   - This setup allows the tests to run as if the IG were published at the
     canonical location.
   - **Steps**:
     1. Host your IG on a local web server (e.g., using Python's
        SimpleHTTPServer or an HTTPS-enabled server).
     2. Modify `resolv.conf` to point `shinny.org` to your local server's IP
        address.
     3. Ensure that your local web server serves the IG over HTTPS to mimic the
        canonical setup.

### IG Test Fixtures

We use the examples of the SHINNYBundleProfile from the URL `https://shinny.org/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile-examples.html` to test various parts of the implementation guide. 

   **The current tests validate the below examples**:
   1. `AHCHRSNQuestionnaireResponseExample` (URL to download this example : `https://shinny.org/ImplementationGuide/HRSN/Bundle-AHCHRSNQuestionnaireResponseExample.html`) .
   2. `AHCHRSNScreeningResponseExample` (URL to download this example : `https://shinny.org/ImplementationGuide/HRSN/Bundle-AHCHRSNScreeningResponseExample.html`)
   3. `NYScreeningResponseExample` (URL to download this example: `https://shinny.org/ImplementationGuide/HRSN/Bundle-NYScreeningResponseExample.html`)
   4. `ObservationAssessmentFoodInsecurityExample` (URL to download this example: `https://shinny.org/ImplementationGuide/HRSN/Bundle-ObservationAssessmentFoodInsecurityExample.html`)
   5. `ServiceRequestExample` (URL to download this example : `https://shinny.org/ImplementationGuide/HRSN/Bundle-ServiceRequestExample.html`)
   6. `TaskCompletedExample` (URL to download this example: `https://shinny.org/ImplementationGuide/HRSN/Bundle-TaskCompletedExample.html`)
   7. `TaskExample` (URL to download this example: `https://shinny.org/ImplementationGuide/HRSN/Bundle-TaskExample.html`)
   8. `TaskOutputProcedureExample` (URL to download this example: `https://shinny.org/ImplementationGuide/HRSN/Bundle-TaskOutputProcedureExample.html`)
    
### Testing on Your Local Machine or CI/CD

To run the SHIN-NY FHIR Implementation Guide Publication Issues tests on your local
machine , follow these steps:

1. **Clone the Main Repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime
   ```

2. **Navigate to the Top-Level Directory**:
   ```bash
   cd hub-prime
   ```

3. **Run the `IgPublicationIssuesTest` Specific Test Case**: To run just the
   `IgPublicationIssuesTest.java` test case, use the following Maven command:
   ```bash
   mvn -Dtest=org.techbd.orchestrate.fhir.IgPublicationIssuesTest test
   ```

4. **Output Locations**: By default, the output will be stored in the
   `target/surefire-reports/` directory within the project. This will include
   details of the test execution, including any failures.

5. **Generate Output in Various Formats**: Maven can be configured to output the
   test results in various formats. You can use the following options:

   - **HTML and XML Reports**:
     ```bash
     mvn surefire-report:report
     ```
     This generates an HTML report of the test results in the
     `target/site/surefire-report.html`.


