import csv
import sys
import json
import os
from frictionless import Package, transform, steps, extract, Check, errors, Checklist
from datetime import datetime, date
import re  # Import required for regular expression handling


# Custom Check Class
class ValidateAnswerCode(Check):
    code = "validate_answer_code"
    Errors = [errors.RowError]

    # Mapping of QUESTION_CODE to their valid ANSWER_CODEs
    QUESTION_ANSWER_MAP = {
        "44250-9": ["la6568-5", "la6569-3", "la6570-1", "la6571-9"],
        "44255-8": ["la6568-5", "la6569-3", "la6570-1", "la6571-9"],
        "68516-4": ["la6111-4", "la13942-0", "la19282-5", "la28855-7", "la28858-1", "la28854-0", "la28853-2", "la28891-2", "la32059-0", "la32060-8"],
        "68517-2": ["la6270-8", "la26460-8", "la18876-5", "la18891-4", "la18934-2"],
        "68524-8": ["la6270-8", "la26460-8", "la18876-5", "la18891-4", "la18934-2"],
        "69858-9": ["la33-6", "la32-8"],
        "69861-3": ["la33-6", "la32-8"],
        "71802-3": ["la31993-1", "la31994-9", "la31995-6"],
        "76513-1": ["la15832-1", "la22683-9", "la31980-8"],
        "88122-7": ["la28397-0", "la6729-3", "la28398-8"],
        "88123-5": ["la28397-0", "la6729-3", "la28398-8"],
        "89555-7": ["la6111-4", "la6112-2", "la6113-0", "la6114-8", "la6115-5", "la10137-0", "la10138-8", "la10139-6"],
        "93030-5": ["la33-6", "la32-8"],
        "93038-8": ["la6568-5", "la13863-8", "la13909-9", "la13902-4", "la13914-9", "la30122-8"],
        "93159-2": ["la6270-8", "la10066-1", "la10082-8", "la10044-8", "la9933-8"],
        "95530-2": ["la6270-8", "la26460-8", "la18876-5", "la18891-4", "la18934-2"],
        "95615-1": ["la6270-8", "la10066-1", "la10082-8", "la16644-9", "la6482-9"],
        "95616-9": ["la6270-8", "la10066-1", "la10082-8", "la16644-9", "la6482-9"],
        "95617-7": ["la6270-8", "la10066-1", "la10082-8", "la16644-9", "la6482-9"],
        "95618-5": ["la6270-8", "la10066-1", "la10082-8", "la16644-9", "la6482-9"],
       # "96778-6": ["la31996-4", "la28580-1", "la31997-2", "la31998-0", "la31999-8", "la32000-4", "la32001-2", "la9-3"],
        "96779-4": ["la33-6", "la32-8", "la32002-0"],
        "96780-2": ["la31981-6", "la31982-4", "la31983-2"],
        "96781-0": ["la31976-6", "la31977-4", "la31978-2", "la31979-0"],
        "96782-8": ["la33-6", "la32-8"],
        "96842-0": ["la6270-8", "la26460-8", "la18876-5", "la18891-4", "la18934-2"],
        "97027-7": ["la33-6", "la32-8"],
    }

    def validate_row(self, row):
        question_code = row.get("QUESTION_CODE")
        answer_code = row.get("ANSWER_CODE")

        if question_code and answer_code:
            # Normalize both codes to lowercase for case-insensitive comparison
            question_code_cf = question_code.casefold()
            answer_code_cf = answer_code.casefold()

            valid_answers = self.QUESTION_ANSWER_MAP.get(question_code_cf)
            if valid_answers and answer_code_cf not in valid_answers:
                note = f"Invalid ANSWER_CODE '{answer_code}' for QUESTION_CODE '{question_code}'"
                yield errors.RowError.from_row(row, note=note)
               

def clean_results(results):
    """Remove empty errorsSummary from results to avoid displaying null/empty arrays"""
    cleaned_results = results.copy()
    if "errorsSummary" in cleaned_results and not cleaned_results["errorsSummary"]:
        del cleaned_results["errorsSummary"]
    return cleaned_results

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
            "pt_info_data": file4,  
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
            cleaned_results = clean_results(results)
            print(json.dumps(cleaned_results, indent=4))
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
            resource_with_path = {**resource, "path": path, "dialect": {
                    "skipInitialSpace": True
            }}  # Add dialect options if needed
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
            ("ORGANIZATION_TYPE_CODE", "organization_type_code"),
            ("ORGANIZATION_TYPE_DISPLAY", "organization_type_display"), 
            ("ORGANIZATION_TYPE_CODE_SYSTEM", "organization_type_code_system"), 
            ("FACILITY_STATE", "facility_state"),
            ("ENCOUNTER_CLASS_CODE", "encounter_class_code"), 
            ("ENCOUNTER_CLASS_CODE_DESCRIPTION", "encounter_class_code_description"), 
            ("ENCOUNTER_CLASS_CODE_SYSTEM", "encounter_class_code_system"),
            ("ENCOUNTER_STATUS_CODE", "encounter_status_code"),
            ("ENCOUNTER_STATUS_CODE_DESCRIPTION", "encounter_status_code_description"),
            ("ENCOUNTER_STATUS_CODE_SYSTEM", "encounter_status_code_system"),
            ("ENCOUNTER_TYPE_CODE_DESCRIPTION", "encounter_type_code_description"),
            ("ENCOUNTER_TYPE_CODE_SYSTEM", "encounter_type_code_system"),
            ("PROCEDURE_STATUS_CODE", "procedure_status_code"), 
            ("PROCEDURE_CODE_SYSTEM", "procedure_code_system"),
            ("CONSENT_STATUS", "consent_status"),
            ("SCREENING_STATUS_CODE", "screening_status_code"),
            ("SCREENING_STATUS_CODE_DESCRIPTION", "screening_status_code_description"),
            ("SCREENING_STATUS_CODE_SYSTEM", "screening_status_code_system"),
            ("SCREENING_LANGUAGE_CODE", "screening_language_code"),
            ("SCREENING_LANGUAGE_DESCRIPTION", "screening_language_description"),
            ("SCREENING_LANGUAGE_CODE_SYSTEM", "screening_language_code_system"),
            ("SCREENING_ENTITY_ID_CODE_SYSTEM", "screening_entity_id_code_system"),
            ("SCREENING_CODE", "screening_code"),
            ("SCREENING_CODE_DESCRIPTION", "screening_code_description"),
            ("SCREENING_CODE_SYSTEM", "screening_code_system"),
            ("QUESTION_CODE_DESCRIPTION", "question_code_description"), 
            ("QUESTION_CODE_SYSTEM", "question_code_system"),
            ("ANSWER_CODE", "answer_code"),
            ("ANSWER_CODE_DESCRIPTION", "answer_code_description"),    
            ("ANSWER_CODE_SYSTEM", "answer_code_system"), 
            ("OBSERVATION_CATEGORY_SDOH_CODE", "observation_category_sdoh_code"),  
            ("OBSERVATION_CATEGORY_SDOH_TEXT", "observation_category_sdoh_text"),
            ("DATA_ABSENT_REASON_CODE", "data_absent_reason_code"),
            ("DATA_ABSENT_REASON_DISPLAY", "data_absent_reason_display"),
            ("POTENTIAL_NEED_INDICATED", "potential_need_indicated"),
            ("ADMINISTRATIVE_SEX_CODE", "administrative_sex_code"), 
            ("ADMINISTRATIVE_SEX_CODE_DESCRIPTION", "administrative_sex_code_description"),
            ("ADMINISTRATIVE_SEX_CODE_SYSTEM", "administrative_sex_code_system"),
            ("SEX_AT_BIRTH_CODE", "sex_at_birth_code"),
            ("SEX_AT_BIRTH_CODE_DESCRIPTION", "sex_at_birth_code_description"),
            ("SEX_AT_BIRTH_CODE_SYSTEM", "sex_at_birth_code_system"),
            ("STATE", "state"),
            ("TELECOM_USE", "telecom_use"),
            ("RACE_CODE_DESCRIPTION", "race_code_description"),
            ("RACE_CODE_SYSTEM", "race_code_system"),
            ("ETHNICITY_CODE_DESCRIPTION", "ethnicity_code_description"),
            ("ETHNICITY_CODE_SYSTEM", "ethnicity_code_system"),
            ("PERSONAL_PRONOUNS_CODE", "personal_pronouns_code"),
            ("PERSONAL_PRONOUNS_DESCRIPTION", "personal_pronouns_description"),
            ("PERSONAL_PRONOUNS_SYSTEM", "personal_pronouns_system"),
            ("GENDER_IDENTITY_CODE", "gender_identity_code"),
            ("GENDER_IDENTITY_CODE_DESCRIPTION", "gender_identity_code_description"),
            ("GENDER_IDENTITY_CODE_SYSTEM", "gender_identity_code_system"),
            ("PREFERRED_LANGUAGE_CODE", "preferred_language_code"),
            ("PREFERRED_LANGUAGE_CODE_DESCRIPTION", "preferred_language_code_description"),
            ("PREFERRED_LANGUAGE_CODE_SYSTEM", "preferred_language_code_system"), 
            ("SEXUAL_ORIENTATION_CODE", "sexual_orientation_code"),
            ("SEXUAL_ORIENTATION_CODE_DESCRIPTION", "sexual_orientation_code_description"),
            ("SEXUAL_ORIENTATION_CODE_SYSTEM", "sexual_orientation_code_system")            
        ]

        for resource in package.resources:
            try:
                # Create transform steps only for fields that exist in the current resource
                transform_steps = [
                    steps.cell_convert(field_name=field_name, function=lambda value: value.lower())
                    for field_name, _ in common_transform_steps
                    if any(field.name == field_name for field in resource.schema.fields)
                ]
                resource = transform(resource, steps=transform_steps)
            except Exception as e:
                file_in_error_path = getattr(resource, "path", None)  # or resource.name, depending on your library
                file_in_error = os.path.basename(file_in_error_path) if file_in_error_path else None
                error_message = str(e)
                # Extract missing field name as before
                match = re.search(r"'(.*?)'", error_message)
                missing_field = match.group(1) if match else "Unknown field"
                file_info = f" in file '{file_in_error}'" if file_in_error else ""
                user_friendly_message = (
                    f"The field '{missing_field}' is missing or incorrectly named in the dataset{file_info}. "
                    f"Please check if it exists in the CSV file and matches the expected schema."
                )
                results["errorsSummary"].append({
                    "fieldName": missing_field,
                    "fileName": file_in_error,
                    "message": user_friendly_message,
                    "type": "data-processing-errors"
                })

        checklist = Checklist(checks=[ValidateAnswerCode()])
        # Validate the package
        report = package.validate(checklist=checklist)  
         

        # Add the validation report to results
        results["report"] = report.to_dict()
        

    except FileNotFoundError as e:
        results["errorsSummary"].append({                        
            "fieldName": None,
            "message": str(e),
            "type": "file-missing-error"
        })
 


    # Write the results to a JSON file if output_path is provided, otherwise print to console
    if output_path:
        with open(output_path, 'w') as json_file:
            try:
                cleaned_results = clean_results(results)
                json.dump(cleaned_results, json_file, indent=4, default=str)
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
            cleaned_results = clean_results(results)
            print(json.dumps(cleaned_results, indent=4, default=str))
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
        cleaned_results = clean_results(results)
        print(json.dumps(cleaned_results, indent=4))
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
        cleaned_results = clean_results(results)
        print(json.dumps(cleaned_results, indent=4))
        sys.exit(1)

    # Run validation
    validate_package(spec_path, file1, file2, file3, file4, output_path)