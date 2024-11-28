
# CSV Validation via Frictionless

## Overview

This project leverages the [Frictionless Data](https://frictionlessdata.io/) library to validate CSV files against predefined schemas, ensuring data quality and consistency. The validation process checks data integrity, format adherence, and schema compliance to streamline data workflows.

## Folder Structure

- **`support/specifications`**
  - `datapackage-nyher-fhir-ig-equivalent.json`: Schema specification for validating CSV files.
  - `validate-nyher-fhir-ig-equivalent.py`: Python script to validate CSV files against the schema.
- **`flat-file/nyher-fhir-ig-example/`**: Folder containing sample CSV files for validation.
  - `DEMOGRAPHIC_DATA_partner1-test-20241128-testcase1.csv`: Demographic information data.
  - `QE_ADMIN_DATA_partner1-test-20241128-testcase1.csv`: QE administration data.
  - `SCREENING_OBSERVATION_DATA_partner1-test-20241128-testcase1.csv`: Primary screening observation data.
  - `SCREENING_LOCATION_DATA_partner1-test-20241128-testcase1.csv`: Location-related screening data.
  - `SCREENING_ENCOUNTER_DATA_partner1-test-20241128-testcase1.csv`: Encounter-related screening data.
  - `SCREENING_CONSENT_DATA_partner1-test-20241128-testcase1.csv`: Consent-related screening data.
  - `SCREENING_RESOURCES_DATA_partner1-test-20241128-testcase1.csv`: Detailed screening resources data.
  - `Consolidated NYHER FHIR IG Examples.xlsx`: Excel file with consolidated sheets of the CSV data above.

- **`documentation.auto.md`**
  - Auto-generated documentation detailing the schema, validation process, and CSV contents for easier understanding.

## Prerequisites

- **Python**: Ensure Python 3.x is installed.
- **Frictionless**: Install the Frictionless library using pip:
  ```bash
  pip install frictionless
  ```
  Visit the [Frictionless Documentation](https://framework.frictionlessdata.io/docs/getting-started.html) for more information.

## Validation Process

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime.git
   cd polyglot-prime/support/specifications
   ```

2. **Run the Validation Script**:
   Use the provided `validate-nyher-fhir-ig-equivalent.py` script to validate all CSV files in the `flat-file/nyher-fhir-ig-example/` directory. Replace filenames as necessary:
   ```bash
   python validate-nyher-fhir-ig-equivalent.py datapackage-nyher-fhir-ig-equivalent.json flat-file/nyher-fhir-ig-example/QE_ADMIN_DATA_partner1-test-20241128-testcase1.csv flat-file/nyher-fhir-ig-example/SCREENING_OBSERVATION_DATA_partner1-test-20241128-testcase1.csv flat-file/nyher-fhir-ig-example/SCREENING_LOCATION_DATA_partner1-test-20241128-testcase1.csv flat-file/nyher-fhir-ig-example/SCREENING_ENCOUNTER_DATA_partner1-test-20241128-testcase1.csv flat-file/nyher-fhir-ig-example/SCREENING_CONSENT_DATA_partner1-test-20241128-testcase1.csv flat-file/nyher-fhir-ig-example/SCREENING_RESOURCES_DATA_partner1-test-20241128-testcase1.csv flat-file/nyher-fhir-ig-example/DEMOGRAPHIC_DATA_partner1-test-20241128-testcase1.csv output.json
   ```

3. **Review Validation Results**:
   Validation results will be saved in `output.json`, detailing any schema mismatches, errors, or warnings.

4. **Understanding `documentation.auto.md`**:
   - The `documentation.auto.md` file is auto-generated and provides a breakdown of the schema's structure, constraints, and expected field details.
   - Use this file as a reference to interpret validation results and align your CSV data with the schema requirements.

## Notes

- The `datapackage-nyher-fhir-ig-equivalent.json` defines schema expectations, including data types, constraints, and relationships.
- The `validate-nyher-fhir-ig-equivalent.py` script integrates with Frictionless for accurate and detailed validation.
- The `output.json` file provides a machine-readable validation report. To understand its structure, refer to the [Frictionless JSON documentation][(https://framework.frictionlessdata.io/docs/guides/validate](https://framework.frictionlessdata.io/docs/guides/validating-data.html)).

## About the File Naming Convention

The CSV file names include a group identifier at the end, formatted as `partner1-test-20241128-testcase1`. Here's what the components signify:

- **`partner1-test`**: The QE name or organization providing the data.
- **`20241128`**: The date the data was generated or collected (in `YYYYMMDD` format).
- **`testcase1`**: A specific identifier for the data case or scenario.

This naming convention helps organize files systematically and allows easy identification of the data source, purpose, and context.

For further assistance, raise a query in the project's [GitHub Issues](https://github.com/tech-by-design/polyglot-prime/issues).
