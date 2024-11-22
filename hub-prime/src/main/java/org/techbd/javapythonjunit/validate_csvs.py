import sys
import json
import os
from frictionless import Package, transform, steps

def validate_package(spec_path, file1, file2, file3, output_path):
    results = {
        "errorsSummary": [],
        "report": None
    }

    try:
        # Load the schema definitions from spec.json
        with open(spec_path) as f:
            spec = json.load(f)

        # Map the files directly from the arguments
        file_mappings = {
            "qe_admin_data": file2,
            "screening_data": file3,
            "demographic_data": file1
        } 
        # Check for missing files
        missing_files = {key: path for key, path in file_mappings.items() if not os.path.isfile(path)}
        if missing_files:
            for resource_name, file_path in missing_files.items():
                results["errorsSummary"].append({
                    "rowNumber": None,
                    "fieldNumber": None,
                    "fieldName": resource_name,
                    "message": f"File for resource '{resource_name}' not found: {file_path}",
                    "type": "file-missing-error"
                })

            # Write errors to output.json and skip further processing
            # with open(output_path, 'w') as json_file:
            #     json.dump(results, json_file, indent=4)
            print(json.dumps(results, indent=4))
            # print(f"Validation skipped due to missing files. Results saved to '{output_path}'.")
            return  # Skip Frictionless validation

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
            ("ORGANIZATION_TYPE", "organization_type"),
            ("FACILITY_STATE", "facility_state"),
            ("ENCOUNTER_CLASS_CODE", "encounter_class_code"),
            ("ENCOUNTER_CLASS_CODE_DESCRIPTION", "encounter_class_code_description"),
            ("ENCOUNTER_STATUS_CODE", "encounter_status_code"),
            ("ENCOUNTER_STATUS_CODE_DESCRIPTION", "encounter_status_code_description"),
            ("ENCOUNTER_TYPE_CODE_DESCRIPTION", "encounter_type_code_description"),
            ("SCREENING_STATUS_CODE", "screening_status_code"),
            ("SCREENING_CODE_DESCRIPTION", "screening_code_description"),
            ("QUESTION_CODE_DESCRIPTION", "question_code_description"),
            ("UCUM_UNITS", "ucum_units"),
            ("SDOH_DOMAIN", "sdoh_domain"),
            ("ANSWER_CODE", "answer_code"),
            ("ANSWER_CODE_DESCRIPTION", "answer_code_description"),
            ("GENDER", "gender"),
            ("SEX_AT_BIRTH_CODE", "sex_at_birth_code"),
            ("SEX_AT_BIRTH_CODE_DESCRIPTION", "sex_at_birth_code_description"),
            ("SEX_AT_BIRTH_CODE_SYSTEM", "sex_at_birth_code_system"),
            ("RELATIONSHIP_PERSON_CODE", "relationship_person_code"),
            ("RELATIONSHIP_PERSON_DESCRIPTION", "relationship_person_description"),
            ("STATE", "state"),
            ("GENDER_IDENTITY_CODE", "gender_identity_code"),
            ("GENDER_IDENTITY_CODE_DESCRIPTION", "gender_identity_code_description"),
            ("GENDER_IDENTITY_CODE_SYSTEM_NAME", "gender_identity_code_system_name"),
            ("SEXUAL_ORIENTATION_CODE", "sexual_orientation_code"),
            ("SEXUAL_ORIENTATION_CODE_DESCRIPTION", "sexual_orientation_code_description"),
            ("PREFERRED_LANGUAGE_CODE", "preferred_language_code"),
            ("PREFERRED_LANGUAGE_CODE_DESCRIPTION", "preferred_language_code_description"),
            ("PREFERRED_LANGUAGE_CODE_SYSTEM_NAME", "preferred_language_code_system_name"),
            ("RACE_CODE_DESCRIPTION", "race_code_description"),
            ("ETHNICITY_CODE_DESCRIPTION", "ethnicity_code_description"),
            ("ETHNICITY_CODE_SYSTEM_NAME", "ethnicity_code_system_name")
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
            "rowNumber": None,
            "fieldNumber": None,
            "fieldName": None,
            "message": str(e),
            "type": "file-missing-error"
        })

    except Exception as e:
        results["errorsSummary"].append({
            "rowNumber": None,
            "fieldNumber": None,
            "fieldName": None,
            "message": str(e),
            "type": "unexpected-error"
        })

    # Write the results to a JSON file
    # with open(output_path, 'w') as json_file:
    #     json.dump(results, json_file, indent=4)
    print(json.dumps(results, indent=4))


    # Print a success or error message to the console
    # if results["errorsSummary"]:
    #     print(f"Validation completed with errors. Results saved to '{output_path}'.")
    # else:
    #     print(f"Validation completed successfully. Results saved to '{output_path}'.")

if __name__ == "__main__":

    results = {
        "errorsSummary": [],
        "report": None
    }

    # Check for the correct number of arguments
    if len(sys.argv) != 6: 
        error_message = "Invalid number of arguments. Please provide the following arguments: <spec_path> <file1> <file2> <file3> <output_path>"
        results["errorsSummary"].append({
        "rowNumber": None,
        "fieldNumber": None,
        "fieldName": None,
        "message": error_message,
        "type": "argument-error"
        })
        # with open("output.json", 'w') as json_file:
        #     json.dump(results, json_file, indent=4)
        print(json.dumps(results, indent=4))
        sys.exit(1)

    # Parse arguments
    spec_path = sys.argv[1]
    file1 = sys.argv[2]
    file2 = sys.argv[3]
    file3 = sys.argv[4]
    output_path = sys.argv[5]

    # Check if output path is valid
    # if not output_path.endswith('.json'):
    #     print(f"Warning: Provided output path '{output_path}' is not a valid JSON file. Defaulting to 'output.json'.")
    #     output_path = "output.json"

    # Check if the paths exist
    if not os.path.isfile(spec_path):
        error_message = f"Error: Specification file '{spec_path}' not found."
        results["errorsSummary"].append({
            "rowNumber": None,
            "fieldNumber": None,
            "fieldName": None,
            "message": error_message,
            "type": "file-missing-error"
        })
        # with open(output_path, 'w') as json_file:
        #     json.dump(results, json_file, indent=4) 
        print(json.dumps(results, indent=4))
        sys.exit(1)        

    # Run validation
    validate_package(spec_path, file1, file2, file3, output_path)

#-------------------------------------------------------------------------
# import sys
# import json
# import os
# from frictionless import Package, transform, steps

# def validate_package(spec_path, file1, file2, file3, output_path):
#     results = {
#         "errorsSummary": [],
#         "report": None
#     }

#     try:
#         # Load the schema definitions from spec.json
#         with open(spec_path) as f:
#             spec = json.load(f)

#         # Map the files directly from the arguments
#         file_mappings = {
#             "qe_admin_data": file2,
#             "screening_data": file3,
#             "demographic_data": file1
#         }
#         print(file_mappings)
#         # Check for missing files
#         missing_files = {key: path for key, path in file_mappings.items() if not os.path.isfile(path)}
#         if missing_files:
#             for resource_name, file_path in missing_files.items():
#                 results["errorsSummary"].append({
#                     "rowNumber": None,
#                     "fieldNumber": None,
#                     "fieldName": resource_name,
#                     "message": f"File for resource '{resource_name}' not found: {file_path}",
#                     "type": "file-missing-error"
#                 })

#             # Write errors to output.json and skip further processing
#             with open(output_path, 'w') as json_file:
#                 json.dump(results, json_file, indent=4)

#             print(f"Validation skipped due to missing files. Results saved to '{output_path}'.")
#             return  # Skip Frictionless validation

#         # Create the package descriptor dynamically, inserting paths from `file_mappings`
#         resources = []
#         for resource in spec["resources"]:
#             # Ensure the file exists for the given resource name
#             path = file_mappings.get(resource["name"])
#             if not path:
#                 raise FileNotFoundError(f"File for resource '{resource['name']}' not found.")

#             # Update the resource dictionary with the path
#             resource_with_path = {**resource, "path": path}
#             resources.append(resource_with_path)

#         # Construct the final package descriptor with dynamic paths
#         package_descriptor = {
#             "name": "csv-validation-using-ig",
#             "resources": resources
#         }

#         # Load the package with Frictionless
#         package = Package(package_descriptor)

#         # Transform and validate
#         common_transform_steps = [
#             ("ORGANIZATION_TYPE", "organization_type"),
#             ("FACILITY_STATE", "facility_state"),
#             ("ENCOUNTER_CLASS_CODE", "encounter_class_code"),
#             ("ENCOUNTER_CLASS_CODE_DESCRIPTION", "encounter_class_code_description"),
#             ("ENCOUNTER_STATUS_CODE", "encounter_status_code"),
#             ("ENCOUNTER_STATUS_CODE_DESCRIPTION", "encounter_status_code_description"),
#             ("ENCOUNTER_TYPE_CODE_DESCRIPTION", "encounter_type_code_description"),
#             ("SCREENING_STATUS_CODE", "screening_status_code"),
#             ("SCREENING_CODE_DESCRIPTION", "screening_code_description"),
#             ("QUESTION_CODE_DESCRIPTION", "question_code_description"),
#             ("UCUM_UNITS", "ucum_units"),
#             ("SDOH_DOMAIN", "sdoh_domain"),
#             ("ANSWER_CODE", "answer_code"),
#             ("ANSWER_CODE_DESCRIPTION", "answer_code_description"),
#             ("GENDER", "gender"),
#             ("SEX_AT_BIRTH_CODE", "sex_at_birth_code"),
#             ("SEX_AT_BIRTH_CODE_DESCRIPTION", "sex_at_birth_code_description"),
#             ("SEX_AT_BIRTH_CODE_SYSTEM", "sex_at_birth_code_system"),
#             ("RELATIONSHIP_PERSON_CODE", "relationship_person_code"),
#             ("RELATIONSHIP_PERSON_DESCRIPTION", "relationship_person_description"),
#             ("STATE", "state"),
#             ("GENDER_IDENTITY_CODE", "gender_identity_code"),
#             ("GENDER_IDENTITY_CODE_DESCRIPTION", "gender_identity_code_description"),
#             ("GENDER_IDENTITY_CODE_SYSTEM_NAME", "gender_identity_code_system_name"),
#             ("SEXUAL_ORIENTATION_CODE", "sexual_orientation_code"),
#             ("SEXUAL_ORIENTATION_CODE_DESCRIPTION", "sexual_orientation_code_description"),
#             ("PREFERRED_LANGUAGE_CODE", "preferred_language_code"),
#             ("PREFERRED_LANGUAGE_CODE_DESCRIPTION", "preferred_language_code_description"),
#             ("PREFERRED_LANGUAGE_CODE_SYSTEM_NAME", "preferred_language_code_system_name"),
#             ("RACE_CODE_DESCRIPTION", "race_code_description"),
#             ("ETHNICITY_CODE_DESCRIPTION", "ethnicity_code_description"),
#             ("ETHNICITY_CODE_SYSTEM_NAME", "ethnicity_code_system_name")
#         ]

#         for resource in package.resources:
#             # Create transform steps only for fields that exist in the current resource
#             transform_steps = [
#                 steps.cell_convert(field_name=field_name, function=lambda value: value.lower())
#                 for field_name, _ in common_transform_steps
#                 if any(field.name == field_name for field in resource.schema.fields)
#             ]
#             resource = transform(resource, steps=transform_steps)

#         # Validate the package
#         report = package.validate()

#         # Add the validation report to results
#         results["report"] = report.to_dict()

#     except FileNotFoundError as e:
#         results["errorsSummary"].append({
#             "rowNumber": None,
#             "fieldNumber": None,
#             "fieldName": None,
#             "message": str(e),
#             "type": "file-missing-error"
#         })

#     except Exception as e:
#         results["errorsSummary"].append({
#             "rowNumber": None,
#             "fieldNumber": None,
#             "fieldName": None,
#             "message": str(e),
#             "type": "unexpected-error"
#         })

#     # Write the results to a JSON file
#     with open(output_path, 'w') as json_file:
#         json.dump(results, json_file, indent=4)

#     # Print a success or error message to the console
#     if results["errorsSummary"]:
#         print(f"Validation completed with errors. Results saved to '{output_path}'.")
#     else:
#         print(f"Validation completed successfully. Results saved to '{output_path}'.")

# if __name__ == "__main__":

#     results = {
#         "errorsSummary": [],
#         "report": None
#     }

#     # Check for the correct number of arguments
#     if len(sys.argv) != 6: 
#         error_message = "Invalid number of arguments. Please provide the following arguments: <spec_path> <file1> <file2> <file3> <output_path>"
#         results["errorsSummary"].append({
#         "rowNumber": None,
#         "fieldNumber": None,
#         "fieldName": None,
#         "message": error_message,
#         "type": "argument-error"
#         })
#         with open("output.json", 'w') as json_file:
#             json.dump(results, json_file, indent=4)
#         print(error_message)
#         sys.exit(1)

#     # Parse arguments
#     spec_path = sys.argv[1]
#     file1 = sys.argv[2]
#     file2 = sys.argv[3]
#     file3 = sys.argv[4]
#     output_path = sys.argv[5]

#     # Check if output path is valid
#     if not output_path.endswith('.json'):
#         print(f"Warning: Provided output path '{output_path}' is not a valid JSON file. Defaulting to 'output.json'.")
#         output_path = "output.json"

#     # Check if the paths exist
#     if not os.path.isfile(spec_path):
#         error_message = f"Error: Specification file '{spec_path}' not found."
#         results["errorsSummary"].append({
#             "rowNumber": None,
#             "fieldNumber": None,
#             "fieldName": None,
#             "message": error_message,
#             "type": "file-missing-error"
#         })
#         with open(output_path, 'w') as json_file:
#             json.dump(results, json_file, indent=4)
#         print(error_message)
#         sys.exit(1)        

#     # Run validation
#     validate_package(spec_path, file1, file2, file3, output_path)
#***************************************************************************
#**ulimate code second
# import json
# import os
# import glob
# from frictionless import Package, Report, transform, steps 


# import sys

# # sys.argv[0] is the script name, and sys.argv[1] is the first argument.
# # if len(sys.argv) < 2:
# #     print("Usage: python3 csv-validate.py <path_to_data>")
# #     sys.exit(1)

# # data_path = sys.argv[2]  # Get the path passed as an argument
# #print(f"Data path provided: {data_path}")
# # Function to find files based on prefix
# def find_file(prefix, base_dir="src/main/java/org/techbd/javapythonintegration/data/"):
#     files = glob.glob(os.path.join(base_dir, f"{prefix}*.csv"))
#     return files[0] if files else None

# # Get the current working directory
# current_directory = os.getcwd()

# # Print the current working directory
# print("Current Directory:", current_directory)
# # Function to find files based on prefix
# # def find_file(prefix, base_dir=None):
# #     # Use the environment variable if base_dir is not provided
# #     base_dir = base_dir or os.environ.get("CSV_DATA_DIR", "data/")
# #     files = glob.glob(os.path.join(base_dir, f"{prefix}*.csv"))
# #     print(f"Searching for files with prefix '{prefix}' in directory '{base_dir}': Found {files}")
# #     return files[0] if files else None

# def validate_package(spec_path, output_path):
#     # Load the schema definitions from spec.json
#     with open(spec_path) as f:
#         spec = json.load(f)

#     # Map prefixes to resources based on file names
#     file_mappings = {
#         "qe_admin_data": find_file("QE_ADMIN_DATA"),
#         "screening_data": find_file("SCREENING"),
#         "demographic_data": find_file("DEMOGRAPHIC_DATA")
#     }

#     # Create the package descriptor dynamically, inserting paths from `file_mappings`
#     resources = []
#     for resource in spec["resources"]:
#         # Ensure the file exists for the given resource name
#         path = file_mappings.get(resource["name"])
#         if not path:
#             raise FileNotFoundError(f"File for resource '{resource['name']}' not found.")
        
#         # Update the resource dictionary with the path
#         resource_with_path = {**resource, "path": path}
#         resources.append(resource_with_path)

#     # Construct the final package descriptor with dynamic paths
#     package_descriptor = {
#         "name": "csv-validation-using-ig",
#         "resources": resources
#     }

#     # Load the package with Frictionless
#     package = Package(package_descriptor)  # Use trusted=True if absolute paths are needed

#     common_transform_steps = [
#         ("ORGANIZATION_TYPE", "organization_type"),
#         ("FACILITY_STATE", "facility_state"),
#         ("ENCOUNTER_CLASS_CODE", "encounter_class_code"),
#         ("ENCOUNTER_CLASS_CODE_DESCRIPTION", "encounter_class_code_description"),
#         ("ENCOUNTER_STATUS_CODE", "encounter_status_code"),
#         ("ENCOUNTER_STATUS_CODE_DESCRIPTION", "encounter_status_code_description"),
#         ("ENCOUNTER_TYPE_CODE_DESCRIPTION", "encounter_type_code_description"),
#         ("SCREENING_STATUS_CODE", "screening_status_code"),
#         ("SCREENING_CODE_DESCRIPTION", "screening_code_description"),
#         ("QUESTION_CODE_DESCRIPTION", "question_code_description"),
#         ("UCUM_UNITS", "ucum_units"),
#         ("SDOH_DOMAIN", "sdoh_domain"),
#         ("ANSWER_CODE", "answer_code"),
#         ("ANSWER_CODE_DESCRIPTION", "answer_code_description"),
#         ("GENDER", "gender"),
#         ("SEX_AT_BIRTH_CODE", "sex_at_birth_code"),
#         ("SEX_AT_BIRTH_CODE_DESCRIPTION", "sex_at_birth_code_description"),
#         ("SEX_AT_BIRTH_CODE_SYSTEM", "sex_at_birth_code_system"),
#         ("RELATIONSHIP_PERSON_CODE", "relationship_person_code"),
#         ("RELATIONSHIP_PERSON_DESCRIPTION", "relationship_person_description"),
#         ("STATE", "state"),
#         ("GENDER_IDENTITY_CODE", "gender_identity_code"),
#         ("GENDER_IDENTITY_CODE_DESCRIPTION", "gender_identity_code_description"),
#         ("GENDER_IDENTITY_CODE_SYSTEM_NAME", "gender_identity_code_system_name"),
#         ("SEXUAL_ORIENTATION_CODE", "sexual_orientation_code"),
#         ("SEXUAL_ORIENTATION_CODE_DESCRIPTION", "sexual_orientation_code_description"),
#         ("PREFERRED_LANGUAGE_CODE", "preferred_language_code"),
#         ("PREFERRED_LANGUAGE_CODE_DESCRIPTION", "preferred_language_code_description"),
#         ("PREFERRED_LANGUAGE_CODE_SYSTEM_NAME", "preferred_language_code_system_name"),
#         ("RACE_CODE_DESCRIPTION", "race_code_description"),
#         ("ETHNICITY_CODE_DESCRIPTION", "ethnicity_code_description"),
#         ("ETHNICITY_CODE_SYSTEM_NAME", "ethnicity_code_system_name")
#     ]

#     for resource in package.resources:
#         # Create transform steps only for fields that exist in the current resource
#         transform_steps = [
#             steps.cell_convert(field_name=field_name, function=lambda value: value.lower())
#             for field_name, _ in common_transform_steps
#             if any(field.name == field_name for field in resource.schema.fields)
#         ]
#         resource = transform(resource, steps=transform_steps)    

#     # Validate the package
#     report = package.validate()

#     # Prepare a list to hold all errors
#     all_errors = []
 
#     # Prepare the results dictionary
#     results = {
#         "errorsSummary": [],
#         "report": report.to_dict()
        
#     }

#     for error in report.flatten(["rowNumber", "fieldNumber", "fieldName", "message", "type"]):
#         results["errorsSummary"].append({
#             "rowNumber": error[0],
#             "fieldNumber": error[1],
#             "fieldName": error[2],
#             "message": error[3],
#             "type": error[4]
#         })

#     # Add custom errors to the results
#     for custom_error in all_errors:
#         results["errorsSummary"].append({
#             "rowNumber": custom_error['row-number'],
#             "fieldNumber": custom_error['field-number'],
#             "fieldName": custom_error['field-name'],
#             "message": custom_error['error'],
#             "type": 'custom-error'
#         })

#     # Write the results to a JSON file
#     with open(output_path, 'w') as json_file:
#         json.dump(results, json_file, indent=4)
#     #print(json.dumps(results, indent=4))

# if __name__ == "__main__":
#     package_path = "/home/jewel/workspaces/github.com/jewelbonnie/polyglot-prime/hub-prime/src/main/java/org/techbd/javapythonintegration/datapackage-ig.json"
#     output_path = "/home/jewel/workspaces/github.com/jewelbonnie/polyglot-prime/hub-prime/src/main/java/org/techbd/javapythonintegration/output/validation_report.json"

#     validate_package(package_path, output_path)


# //working code
# import json
# import sys
# import logging
# from frictionless import Package, Resource, Report
# from pathlib import Path

# # Set up logging
# logging.basicConfig(
#     level=logging.DEBUG,
#     format='%(asctime)s - %(levelname)s - %(message)s',
#     filename='csv_validation.log'
# )

# def validate_csv_group(csv_files, schema_path=None):
#     """
#     Validates a group of CSV files and returns a validation report.
#     """
#     try:
#         logging.info(f"Starting validation for files: {csv_files}")
#         results = {
#             "groupValidation": {},
#             "errorsSummary": []
#         }

#         for csv_file in csv_files:
#             try:
#                 # Check if file exists
#                 if not Path(csv_file).exists():
#                     logging.error(f"File not found: {csv_file}")
#                     raise FileNotFoundError(f"CSV file not found: {csv_file}")

#                 logging.info(f"Validating file: {csv_file}")
                
#                 # Create a resource for each CSV
#                 resource = Resource(path=csv_file)
#                 if schema_path:
#                     resource.schema = schema_path
                
#                 # Validate the resource
#                 report = resource.validate()
                
#                 file_results = {
#                     "valid": report.valid,
#                     "errors": []
#                 }
                
#                 if not report.valid:
#                     for error in report.flatten(["rowNumber", "fieldNumber", "fieldName", "message", "type"]):
#                         error_detail = {
#                             "rowNumber": error[0],
#                             "fieldNumber": error[1],
#                             "fieldName": error[2],
#                             "message": error[3],
#                             "type": error[4]
#                         }
#                         file_results["errors"].append(error_detail)
#                         results["errorsSummary"].append({
#                             "file": csv_file,
#                             **error_detail
#                         })
                
#                 results["groupValidation"][csv_file] = file_results
#                 logging.info(f"Completed validation for: {csv_file}")

#             except Exception as e:
#                 logging.error(f"Error processing file {csv_file}: {str(e)}", exc_info=True)
#                 results["groupValidation"][csv_file] = {
#                     "valid": False,
#                     "errors": [{"message": f"Processing error: {str(e)}"}]
#                 }

#         return results

#     except Exception as e:
#         logging.error(f"Global validation error: {str(e)}", exc_info=True)
#         raise

# if __name__ == "__main__":
#     try:
#         if len(sys.argv) < 2:
#             logging.error("No CSV files provided")
#             print("Usage: python validate_csvs.py file1.csv file2.csv ...")
#             sys.exit(1)
        
#         csv_files = sys.argv[1:]
#         logging.info(f"Starting validation with arguments: {csv_files}")
        
#         results = validate_csv_group(csv_files)
#         print(json.dumps(results, indent=2))
#         logging.info("Validation completed successfully")
#         sys.exit(0)
        
#     except Exception as e:
#         logging.error(f"Script execution failed: {str(e)}", exc_info=True)
#         print(json.dumps({
#             "error": str(e),
#             "status": "failed"
#         }))
#         sys.exit(1)


#----------end-------------------------------------
# # validate_csvs.py
# import json
# from frictionless import Package, Resource, Report

# def validate_csv_group(csv_files, schema_path=None):
#     """
#     Validates a group of CSV files and returns a validation report.
    
#     Args:
#         csv_files (list): List of paths to CSV files
#         schema_path (str, optional): Path to schema file if any
    
#     Returns:
#         dict: Validation results
#     """
#     results = {
#         "groupValidation": {},
#         "errorsSummary": []
#     }
    
#     for csv_file in csv_files:
#         # Create a resource for each CSV
#         resource = Resource(csv_file)
#         if schema_path:
#             resource.schema = schema_path
            
#         # Validate the resource
#         report = resource.validate()
        
#         file_results = {
#             "valid": report.valid,
#             "errors": []
#         }
        
#         if not report.valid:
#             for error in report.flatten(["rowNumber", "fieldNumber", "fieldName", "message", "type"]):
#                 error_detail = {
#                     "rowNumber": error[0],
#                     "fieldNumber": error[1],
#                     "fieldName": error[2],
#                     "message": error[3],
#                     "type": error[4]
#                 }
#                 file_results["errors"].append(error_detail)
#                 results["errorsSummary"].append({
#                     "file": csv_file,
#                     **error_detail
#                 })
                
#         results["groupValidation"][csv_file] = file_results
    
#     return results

# if __name__ == "__main__":
#     import sys
#     if len(sys.argv) < 2:
#         print("Usage: python validate_csvs.py file1.csv file2.csv ...")
#         sys.exit(1)
        
#     results = validate_csv_group(sys.argv[1:])
#     print(json.dumps(results, indent=2))