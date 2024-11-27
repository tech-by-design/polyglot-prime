
# CSV Validation via Frictionless

## Overview

This project uses Frictionless Data to validate CSV files against predefined schemas and ensure data quality. The validation process checks data consistency, format, and adherence to specifications using Frictionless.

## Folder Structure

- **`support/specifications`**
  - `datapackage-ig.json`: Schema specification for CSV validation.
  - `csv-validate.py`: Script to validate CSV files against `datapackage-ig.json`.
- **`data/`**: Folder containing CSV files for validation.
  - `DEMOGRAPHIC_DATA.csv`: CSV file containing demographic information.
  - `QE_ADMIN_DATA.csv`: CSV file containing QE admin data.
  - `SCREENING_OBSERVATION_DATA.csv`: CSV file containing primary observation data information.
  - `SCREENING_LOCATION_DATA.csv`: CSV file containing location-related screening data information.
  - `SCREENING_ENCOUNTER_DATA.csv`: CSV file containing encounter-related screening data information.
  - `SCREENING_CONSENT_DATA.csv`: CSV file containing consent-related screening data information.
  - `SCREENING_RESOURCES_DATA.csv`: CSV file containing detailed screening data information.

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
   Use the `csv-validate.py` script to validate all CSV files in the `data/` folder:
   ```bash
   python csv-validate.py datapackage-ig.json data/QE_ADMIN_DATA.csv data/SCREENING_OBSERVATION_DATA.csv data/SCREENING_LOCATION_DATA.csv data/CREENING_ENCOUNTER_DATA.csv data/SCREENING_CONSENT_DATA.csv data/SCREENING_RESOURCES_DATA.csv data/DEMOGRAPHIC_DATA.csv output.json
   ```

3. **View Validation Results**:
   The script will output validation results, showing any errors or issues found in the CSV files.

## Notes

- The `datapackage-ig.json` file defines the expected schema, including data types, constraints, and other validation rules for each CSV file.
- The `csv-validate.py` script utilizes Frictionless to validate the CSV files and report any deviations from the schema and saved into `output.json` file.

For further assistance or questions, please refer to GitHub Issues.
