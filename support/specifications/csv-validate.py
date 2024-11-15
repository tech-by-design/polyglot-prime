import json
import os
import glob
from frictionless import Package, Report, transform, steps 

# Function to find files based on prefix
def find_file(prefix, base_dir="data/"):
    files = glob.glob(os.path.join(base_dir, f"{prefix}*.csv"))
    return files[0] if files else None

def validate_package(spec_path, output_path):
    # Load the schema definitions from spec.json
    with open(spec_path) as f:
        spec = json.load(f)

    # Map prefixes to resources based on file names
    file_mappings = {
        "qe_admin_data": find_file("QE_ADMIN_DATA"),
        "screening_data": find_file("SCREENING"),
        "demographic_data": find_file("DEMOGRAPHIC_DATA")
    }

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

    # Prepare a list to hold all errors
    all_errors = []
 
    # Prepare the results dictionary
    results = {
        "errorsSummary": [],
        "report": report.to_dict()
        
    }

    for error in report.flatten(["rowNumber", "fieldNumber", "fieldName", "message", "type"]):
        results["errorsSummary"].append({
            "rowNumber": error[0],
            "fieldNumber": error[1],
            "fieldName": error[2],
            "message": error[3],
            "type": error[4]
        })

    # Add custom errors to the results
    for custom_error in all_errors:
        results["errorsSummary"].append({
            "rowNumber": custom_error['row-number'],
            "fieldNumber": custom_error['field-number'],
            "fieldName": custom_error['field-name'],
            "message": custom_error['error'],
            "type": 'custom-error'
        })

    # Write the results to a JSON file
    with open(output_path, 'w') as json_file:
        json.dump(results, json_file, indent=4)
    #print(json.dumps(results, indent=4))

if __name__ == "__main__":
    package_path = "datapackage-ig.json"
    output_path = "validation_report.json"
    validate_package(package_path, output_path)
