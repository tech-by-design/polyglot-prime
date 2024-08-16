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

## SHIN-NY FHIR Implementation Guide Assurance

Within this directory, you will find specific tests aimed at verifying
compliance with the SHIN-NY FHIR Implementation Guide (IG). The primary test
class for this purpose is located in
[org/techbd/orchestrate/fhir/ImplGuideTest.java](org/techbd/orchestrate/fhir/ImplGuideTest.java);

#### More about the Implementation Guide
The FHIR v4 Implementation Guide you provided primarily defines the "SHINNY Bundle Profile," which is an extension of the base FHIR Bundle. This profile introduces additional constraints and rules for working with bundles in specific healthcare contexts. Here’s a summary of its key contents:

**StructureDefinition:**
The SHINNY Bundle Profile extends the base FHIR Bundle resource, imposing additional constraints to ensure interoperability and adherence to specific healthcare use cases.
The profile is in the draft stage and is identified by the URL http://localhost:8000/ImplementationGuide/HRSN.

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

The Implementation Guide being tested is in
[test/resources/org/techbd/fixtures](test/resources/org/techbd/fixtures)
directory called `ImplementationGuide.json`.

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

### IG Source and Test Fixtures

We use Synthea to generate non-scenario-based JSON test fixtures that rigorously
test various parts of the implementation guide. These test fixtures include both
"happy" (successful) and "unhappy" (failure) paths and can be found in
[test/resources/org/techbd/fixtures](test/resources/org/techbd/fixtures)
directory.

The `README.md` file in that directory provides detailed explanations of the
fixtures and their intended usage.

### Testing on Your Local Machine or CI/CD

To run the SHIN-NY FHIR Implementation Guide Assurance tests on your local
machine or within a CI/CD pipeline, follow these steps:

1. **Clone the Main Repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime
   ```

2. **Navigate to the Top-Level Directory**:
   ```bash
   cd hub-prime
   ```

3. **Run the `ImplGuideTest` Specific Test Case**: To run just the
   `ImplGuideTest.java` test case, use the following Maven command:
   ```bash
   mvn -Dtest=org.techbd.orchestrate.fhir.ImplGuideTest test
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


### Unhappy path test fixtures:
Details of the generated unhappy path fixture files and a summary of the new errors introduced:
1. **Modified_FHIR_with_10_errors.json**
   1. **Invalid Resource Type**: Changed one of the resource types to an invalid type.
   2. **Missing Required Element**: Removed a required element from one of the resources.
   3. **Incorrect Data Type**: Changed the data type of a specific element to an incompatible type.
   4. **Invalid Code in Coding System**: Replaced a valid code with an invalid code in a coding system.
   5. **Incorrect Reference Format**: Modified a reference URL to an incorrect format.
   6. **Duplicate Identifier**: Added a duplicate identifier within a resource that should have unique identifiers.
   7. **Inconsistent Profile Reference**: Changed a profile reference to an incorrect or non-existent profile.
   8. **Invalid Date Format**: Modified a date to an invalid format that does not conform to the expected pattern.
   9. **Conflicting Data in Extension**: Introduced conflicting data within an extension.
   10. **Missing Profile Declaration**: Removed the profile declaration from one of the resources.
2. **Modified_FHIR_with_10_different_errors.json**
   1. **Missing Resource ID**: Removed the id element from one of the resources.
   2. **Invalid System URL**: Changed a valid system URL to an invalid one.
   3. **Incorrect Value Type in Extension**: Changed a valueDecimal to an incorrect valueString.
   4. **Invalid Period Format**: Altered the period format in an Encounter resource to an invalid format.
   5. **Missing Coding System**: Removed the system field from a coding entry.
   6. **Incorrect Full URL Format**: Modified the fullUrl to an invalid format.
   7. **Duplicate Resource Entry**: Duplicated a resource entry in the Bundle.
   8. **Missing Reference in Condition**: Removed the reference field from the Condition subject.
   9. **Invalid Coding Display Text**: Changed the display text in a coding system to an invalid value.
   10. **Incorrect Resource Method in Request**: Changed the request method from POST to an invalid value.