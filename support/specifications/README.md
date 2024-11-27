
# CSV Validation via Frictionless

## Overview

This project uses Frictionless Data to validate CSV files against predefined schemas and ensure data quality. The validation process checks data consistency, format, and adherence to specifications using Frictionless.

## Folder Structure

- **`support/specifications`**
  - `datapackage-nyher-fhir-ig-equivalent.json`: Schema specification for CSV validation.
  - `validate-nyher-fhir-ig-equivalent.py`: Script to validate CSV files against `datapackage-nyher-fhir-ig-equivalent.json`.
- **`flat-file/nyher-fhir-ig-example/`**: Folder containing CSV files for validation.
  - `DEMOGRAPHIC_DATA.csv`: CSV file containing demographic information.
  - `QE_ADMIN_DATA.csv`: CSV file containing QE admin data.
  - `SCREENING_OBSERVATION_DATA.csv`: CSV file containing primary observation data information.
  - `SCREENING_LOCATION_DATA.csv`: CSV file containing location-related screening data information.
  - `SCREENING_ENCOUNTER_DATA.csv`: CSV file containing encounter-related screening data information.
  - `SCREENING_CONSENT_DATA.csv`: CSV file containing consent-related screening data information.
  - `SCREENING_RESOURCES_DATA.csv`: CSV file containing detailed screening data information.
  - `Consolidated NYHER FHIR IG Examples.xlsx`: Excel file containing consolidated sheets of the above CSVs.

## Prerequisites

- **Python**: Ensure Python 3.x is installed on your machine.
- **Frictionless**: Install Frictionless using pip:
  ```bash
  pip install frictionless
  ```

## Validation Process

1. **Clone the repository**:
   ```bash
   git clone https://github.com/tech-by-design/polyglot-prime.git
   cd polyglot-prime/support/specifications
   ```

2. **Run the validation script**:
   Use the `validate-nyher-fhir-ig-equivalent.py` script to validate all CSV files in the `flat-file/nyher-fhir-ig-example/` folder:
   ```bash
   python validate-nyher-fhir-ig-equivalent.py datapackage-nyher-fhir-ig-equivalent.json flat-file/nyher-fhir-ig-example/QE_ADMIN_DATA.csv flat-file/nyher-fhir-ig-example/SCREENING_OBSERVATION_DATA.csv flat-file/nyher-fhir-ig-example/SCREENING_LOCATION_DATA.csv flat-file/nyher-fhir-ig-example/SCREENING_ENCOUNTER_DATA.csv flat-file/nyher-fhir-ig-example/SCREENING_CONSENT_DATA.csv flat-file/nyher-fhir-ig-example/SCREENING_RESOURCES_DATA.csv flat-file/nyher-fhir-ig-example/DEMOGRAPHIC_DATA.csv output.json
   ```

3. **View Validation Results**:
   The script will output validation results, showing any errors or issues found in the CSV files.

## Notes

- The `datapackage-nyher-fhir-ig-equivalent.json` file defines the expected schema, including data types, constraints, and other validation rules for each CSV file.
- The `validate-nyher-fhir-ig-equivalent.py` script utilizes Frictionless to validate the CSV files and report any deviations from the schema and saved into `output.json` file.

For further assistance or questions, please refer to GitHub Issues.
