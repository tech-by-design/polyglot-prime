
# CSV Validation Service via FastAPI 

## Overview

This project provides a robust solution for validating CSV files using the [Frictionless Framework](https://frictionlessdata.io/) and exposing the validation service through a [FastAPI](https://fastapi.tiangolo.com/) backend.

## Features 

- **CSV Schema Validation**: Ensures CSV files conform to predefined schemas.
- **Flat File and JSON Specifications**: Detailed specifications for flat file validations and JSON schemas are available [here](https://github.com/megin1989/polyglot-prime/blob/main/support/specifications/flat-file/README.md).
- **Mapping CSV Fields to FHIR IG Elements**: A detailed mapping of each field in the provided CSV files to their corresponding FHIR IG elements is documented in the [`documentation.auto.md`](https://github.com/tech-by-design/polyglot-prime/blob/main/support/specifications/flat-file/documentation.auto.md) file. This file provides a comprehensive field-by-field mapping and serves as a guide to understanding how the CSV files translate into the FHIR resources.
- **File Upload Examples**: Examples of file uploads are detailed in the [nyher-fhir-ig-example](https://github.com/tech-by-design/polyglot-prime/blob/main/support/specifications/flat-file/nyher-fhir-ig-example/README.md).
- **FastAPI Integration**: Exposes the validation functionality via a RESTful API.
- **Detailed Error Reporting**: Highlights specific issues in the uploaded CSV files.
- **Lightweight and Scalable**: Built with Python for performance and ease of use.

## Technologies Used

- **Frictionless Framework**: For defining schemas and validating CSV files.
- **FastAPI**: For creating a fast and modern web API.
- **Python**: The core programming language for this project.


## Installation 


Before you can use this tool, make sure you have the following installed on your system:

- **Python 3.x**:
  Ensure that Python 3 is installed on your system. You can check if Python 3 is already installed by running the following command:

  ```bash
  python3 --version
  ```
  If Python 3 is not installed, follow the instructions below to install it:
    - Ubuntu/Debian-based systems:
      ```bash
      sudo apt update
      sudo apt install python3
      ```
    - macOS (using Homebrew):
      ```bash
      brew install python
    - Windows: Download and install the latest version of Python from the official website: https://www.python.org/downloads/

- **pip (Python Package Installer)**: pip is the package manager for Python and is needed to install libraries like Frictionless.

  Check if pip is installed by running:
  ```bash
  python3 -m pip --version
  ```
  If pip is not installed, follow these steps:
    - On Ubuntu/Debian-based systems:
      ```bash
        sudo apt install python3-pip
      ```
    - On macOS (using Homebrew):
      ```bash
        brew install pip
      ```
    - On Windows: If pip isn't already installed with Python, you can get it from the [official Python pip installation guide](https://pip.pypa.io/en/stable/installation/).

  ***Troubleshooting***: 
    If you encounter errors like No module named ensurepip, it's possible that your Python installation is missing the ensurepip module, which is typically used to install pip. In this case, install pip manually using the package manager for your operating system, as described above. Alternatively, you can use the following command to install pip if it's missing:
      ```bash
        python3 -m ensurepip --upgrade
      ```

### Steps to set up the FastAPI service

1. Create a virtual environment:

   ```python3 -m venv venv```

2. Activate the virtual environment:

   ```source venv/bin/activate.fish```

3. Install the requirements from the requirements.txt file to the virtual environment:
    
   ```pip install -r requirements.txt```

4. Prepare the application environment using the configuration file by running:

   ```source .env```

5. Run the FastAPI application:
   
  ```uvicorn main:app --reload```


## About the File Naming Convention

The CSV file names in this project follow a strict naming convention to ensure consistency and compatibility with the validation process. Each file name is structured as follows:

**`<DATA_TYPE>_<GROUP_IDENTIFIER>.csv`**

### Components of the File Name

1. **`<DATA_TYPE>`**:
   - This is the predefined and mandatory part of the file name. It indicates the type of data contained in the file and must remain unchanged.
   - Examples of valid values:
     - `QE_ADMIN_DATA_` 
     - `SCREENING_PROFILE_DATA_` 
     - `SCREENING_OBSERVATION_DATA_`
     - `DEMOGRAPHIC_DATA_`     

2. **`<GROUP_IDENTIFIER>`**:
   - This part of the file name is flexible and includes the following components:
     - **QE Name or Organization**: Represents the entity providing the data (e.g., `partner1-test`).
     - **Date**: The date the data was generated or collected, formatted as `YYYYMMDD` (e.g., `20241128`).
     - **Test Case or Scenario Identifier**: A specific identifier to distinguish different test cases or scenarios (e.g., `testcase1`).

### Example File Names

- `QE_ADMIN_DATA_partner1-test-20241128-testcase1.csv` 
- `SCREENING_PROFILE_DATA_partner1-test-20241128-testcase1.csv` 
- `SCREENING_OBSERVATION_DATA_partner1-test-20241128-testcase1.csv`
- `DEMOGRAPHIC_DATA_partner1-test-20241128-testcase1.csv`
