
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
  - `SCREENING_DATA.csv`: CSV file containing screening data.

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
   python csv-validate.py
   ```

3. **View Validation Results**:
   The script will output validation results, showing any errors or issues found in the CSV files.

## Notes

- The `datapackage-ig.json` file defines the expected schema, including data types, constraints, and other validation rules for each CSV file.
- The `csv-validate.py` script utilizes Frictionless to validate the CSV files and report any deviations from the schema.

For further assistance or questions, please refer to GitHub Issues.
