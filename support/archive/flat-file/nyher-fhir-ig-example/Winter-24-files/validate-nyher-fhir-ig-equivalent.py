import csv
import sys
import json
import os
from frictionless import Package, transform, steps, extract
from datetime import datetime, date
import re  # Import required for regular expression handling


def validate_package(spec_path, file1, file2, file3, file4, output_path):
    results = {
        "errorsSummary": [],
        "report": None,
        "originalData": {}  # To store original processed data
    }

    try:
        # Load the schema definitions from spec.json
        with open(spec_path) as f:
            spec = json.load(f)

        # Map the files directly from the arguments
        file_mappings = {
            "qe_admin_data": file1,
            "screening_profile_data": file2,             
            "screening_observation_data": file3,
            "demographic_data": file4,  
        } 
        # Check for missing files
        missing_files = {key: path for key, path in file_mappings.items() if not os.path.isfile(path)}
        if missing_files:
            for resource_name, file_path in missing_files.items():
                results["errorsSummary"].append({                                       
                    "fieldName": resource_name,
                    "message": f"File for resource '{resource_name}' not found: {file_path}",
                    "type": "file-missing-error"
                })

            # Write errors to output.json and skip further processing
            print(json.dumps(results, indent=4))
            return  # Skip Frictionless validation
        
                # Only extract data if there are no errors in the summary
        if not results["errorsSummary"]:
            # Parse CSV files into JSON format using `extract` and store in results["originalData"]
            for resource_name, file_path in file_mappings.items():
                rows = extract(file_path)  # Extract data from CSV
                results["originalData"][resource_name] = rows
        

        # Create the package descriptor dynamically, inserting paths from `file_mappings`
        resources = []
        for resource in spec["resources"]:
            # Ensure the file exists for the given resource name
            path = file_mappings.get(resource["name"])
            if not path:
                raise FileNotFoundError(f"File for resource '{resource['name']}' not found.")

            # Update the resource dictionary with the path
            resource_with_path = {**resource, "path": path}
            resources.append(resource_with_path)

        # Construct the final package descriptor with dynamic paths
        package_descriptor = {
            "name": "csv-validation-using-ig",
            "resources": resources
        }

        # Load the package with Frictionless
        package = Package(package_descriptor)

        # Transform and validate
        common_transform_steps = [ 
            ("ORGANIZATION_TYPE_DISPLAY", "organization_type_display"),    
            ("SCREENING_CODE_DESCRIPTION", "screening_code_description"),
            ("QUESTION_CODE_TEXT", "question_code_text"),  
            ("ANSWER_CODE", "answer_code"),
            ("ANSWER_CODE_DESCRIPTION", "answer_code_description"), 
            ("EXTENSION_SEX_AT_BIRTH_CODE_VALUE", "extension_sex_at_birth_code_value"),
            ("RELATIONSHIP_PERSON_CODE", "relationship_person_code"),
            ("RELATIONSHIP_PERSON_DESCRIPTION", "relationship_person_description"), 
            ("EXTENSION_GENDER_IDENTITY_DISPLAY", "extension_gender_identity_display"),            
            ("GENDER_IDENTITY_CODE", "gender_identity_code"),  
            ("SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION", "sexual_orientation_value_code_description"), 
            ("PREFERRED_LANGUAGE_CODE_SYSTEM_NAME", "preferred_language_code_system_name"), 
            ("EXTENSION_OMBCATEGORY_RACE_CODE", "extension_ombcategory_race_code"),
            ("EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION", "extension_ombcategory_race_code_description"),  
            ("EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION", "extension_ombcategory_ethnicity_code_description"),    
            ("OBSERVATION_CATEGORY_SDOH_DISPLAY","observation_category_sdoh_display"),  
            ("QUESTION_CODE_DISPLAY","question_code_display"),   
            ("DATA_ABSENT_REASON_DISPLAY", "data_absent_reason_display")
        ]

        for resource in package.resources:
            # Create transform steps only for fields that exist in the current resource
            transform_steps = [
                steps.cell_convert(field_name=field_name, function=lambda value: value.lower())
                for field_name, _ in common_transform_steps
                if any(field.name == field_name for field in resource.schema.fields)
            ]
            resource = transform(resource, steps=transform_steps)

        # Validate the package
        report = package.validate()

        # Add the validation report to results
        results["report"] = report.to_dict()

    except FileNotFoundError as e:
        results["errorsSummary"].append({                        
            "fieldName": None,
            "message": str(e),
            "type": "file-missing-error"
        })

    except Exception as e:
        error_message = str(e)

        # Check if the error is related to missing fields in transformation
        if "selection is not a field or valid field index" in error_message:
            # Extract the missing field name from the error message
            match = re.search(r"'(.*?)'", error_message)
            missing_field = match.group(1) if match else "Unknown field"

            user_friendly_message = (
                f"The field '{missing_field}' is missing or incorrectly named in the dataset. "
                f"Please check if it exists in the CSV file and matches the expected schema."
            )
        else:
            user_friendly_message = error_message  # Keep other errors as-is

        results["errorsSummary"].append({
            "fieldName": None,
            "message": user_friendly_message,
            "type": "data-processing-errors"
        })


    # Write the results to a JSON file if output_path is provided, otherwise print to console
    if output_path:
        with open(output_path, 'w') as json_file:
            try:
                json.dump(results, json_file, indent=4, default=str)
                print(f"Validation results written to '{output_path}'.")
                return True
            except Exception as e:
                results["errorsSummary"].append({
                "fieldName": None,
                "message": f"Error converting results to JSON: {str(e)}",
                "type": "data-processing-errors"
                }) 
                print(results)
                return False

    else: 
        try:
            print(json.dumps(results, indent=4, default=str))
            return True
        except Exception as e:
            results["errorsSummary"].append({
            "fieldName": None,
            "message": f"Error converting results to JSON: {str(e)}",
            "type": "data-processing-errors"
            }) 
            print(results)
            return False

if __name__ == "__main__":

    results = {
        "errorsSummary": [],
        "report": None
    }

    # Check for the correct number of arguments
    if len(sys.argv) < 6 or len(sys.argv) > 7: 
        error_message = "Invalid number of arguments. Please provide the following arguments: <spec_path> <file1> <file2> <file3> <file4> <file5> <file6> <file7> [output_path]"
        results["errorsSummary"].append({                
        "fieldName": None,
        "message": error_message,
        "type": "argument-error"
        })
        print(json.dumps(results, indent=4))
        sys.exit(1)

    # Parse arguments
    spec_path = sys.argv[1]
    file1 = sys.argv[2]
    file2 = sys.argv[3]
    file3 = sys.argv[4]
    file4 = sys.argv[5] 
    output_path = sys.argv[6] if len(sys.argv) > 6 else None  # Allow no output_path

    # Validate and adjust output_path
    if output_path:  # If output_path is provided
        if not output_path.endswith('.json'):
            print(f"Warning: Provided output path '{output_path}' is not a valid JSON file. Defaulting to 'output.json'.")
            output_path = "output.json"

    # Check if the paths exist
    if not os.path.isfile(spec_path):
        error_message = f"Error: Specification file '{spec_path}' not found."
        results["errorsSummary"].append({                       
            "fieldName": None,
            "message": error_message,
            "type": "file-missing-error"
        }) 
        print(json.dumps(results, indent=4))
        sys.exit(1)        

    # Run validation
    validate_package(spec_path, file1, file2, file3, file4, output_path)