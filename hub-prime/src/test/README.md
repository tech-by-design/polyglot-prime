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
