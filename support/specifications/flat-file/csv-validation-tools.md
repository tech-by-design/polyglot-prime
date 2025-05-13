
# Tools for Data Validation and Exploration

## Data Curator

[Data Curator](https://github.com/qcif/data-curator) is a simple desktop data editor designed to help describe, validate, and share usable open data. It is a lightweight and user-friendly tool that provides a graphical interface for editing tabular data while ensuring adherence to data standards.

### Key Features:
- **Schema Editing**: Easily modify Frictionless JSON schemas to suit your project's requirements.
- **Load and Preview Data**: Visualize CSV files and inspect their contents in a user-friendly interface.
- **Validate Against Schema**: Perform schema validation using the Frictionless JSON specification provided in this repository.
- **Edit and Save**: Correct errors directly in the application and save validated data.
- **Metadata Management**: Add metadata for enhanced data usability.
- **Export Options**: Share validated datasets as clean CSVs or complete data packages.

### How to Use:
1. **Install Data Curator**: Download it [here](https://github.com/qcif/data-curator) and follow the instructions for your operating system.
2. **Load the Example Data Package**: 
   - Access the example data package from our repository: [Data Package Examples](https://github.com/tech-by-design/polyglot-prime/tree/main/support/specifications/flat-file).
   - The data package contains only the Frictionless JSON schema (`datapackage-nyher-fhir-ig-equivalent.json`) and the CSV data folder (`nyher-fhir-ig-example`). The CSV folder includes the following files:
      ```
      nyher-fhir-ig-example/
      ├── SDOH_QEadmin_CareRidgeSCN_testcase1_20250312040214.csv 
      ├── SDOH_ScreeningProf_CareRidgeSCN_testcase1_20250312040214.csv 
      ├── SDOH_ScreeningObs_CareRidgeSCN_testcase1_20250312040214.csv
      └── SDOH_PtInfo_CareRidgeSCN_testcase1_20250312040214.csv 
      ```
      Other files (e.g., Python scripts or markdown documentation) have been removed for clarity.
   - Download and zip the `specifications` folder for easy sharing or validation in Data Curator.
3. **Ensure Correct CSV Order**
   - After loading the CSV package into Data Curator, ensure that the files are listed and processed in the following order (due to the primary key reference in `QE_ADMIN_DATA`):
     1. `SDOH_QEadmin_CareRidgeSCN_testcase1_20250312040214.csv` 
     2. `SDOH_ScreeningProf_CareRidgeSCN_testcase1_20250312040214.csv `      
     3. `SDOH_ScreeningObs_CareRidgeSCN_testcase1_20250312040214.csv` 
     4. `SDOH_PtInfo_CareRidgeSCN_testcase1_20250312040214.csv `
   - This order is required because `QE_ADMIN_DATA` has the primary key `PATIENT_MR_ID_VALUE`, and the other datasets reference it.
4. **Validate and Save**
   - Load the zipped data package in Data Curator.
   - Fix any errors identified during validation and save the updated files.

## Open Data Editor (ODE)

The [Open Data Editor (ODE)](https://opendataeditor.okfn.org/documentation/getting-started/) is a desktop tool designed for non-technical data practitioners to explore, create schemas, and work with tabular datasets. It provides an interface for creating data packages and basic data editing.

### Key Features:
- **Desktop Application**: Requires installation on your local machine.
- **Basic Error Detection**: Identify basic formatting issues in data.
- **Schema Creation**: Create schemas for your datasets and define field types and properties.
- **Interactive Editing**: Edit data and metadata within the interface.
- **Export Options**: Save files as data packages for further use.

### How to Use:
1. Visit the [Open Data Editor website](https://opendataeditor.okfn.org/documentation/getting-started/) and install it.
2. Upload your dataset (CSV files) from the example folder: [Example Data](https://github.com/tech-by-design/polyglot-prime/tree/main/support/specifications/flat-file/nyher-fhir-ig-example).
3. Define schema and field properties for your data.
4. Review and edit data as needed.
5. Export as a data package.

## Choosing Between Data Curator and ODE

| Feature                  | Data Curator                      | Open Data Editor (ODE)           |
|--------------------------|------------------------------------|-----------------------------------|
| **Platform**             | Desktop application               | Desktop application                        |
| **Installation**         | Required                          | Required                             |
| **Schema Validation**    | Yes                               | No (only schema creation)               |
| **Error Correction**     | Yes                               | Yes, Basic editing                              |
| **Metadata Management**  | Supported                         | Limited                          |
| **Ideal for**            | Data package validation, comprehensive data quality checks | Basic CSV editing, schema creation, simple data package creation |

 