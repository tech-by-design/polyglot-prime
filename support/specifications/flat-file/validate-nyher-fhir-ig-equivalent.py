import csv
import sys
import json
import os
from frictionless import Package, transform, steps, extract, Check, errors, Checklist, Resource, Pipeline
from datetime import datetime, date
import re  # Import required for regular expression handling
import calendar  # Import for leap year checking


class OptionalYesNoFlagsCheck(Check):
    """Custom check for optional Yes/No flag fields"""
    code = "optional-yes-no-flags"
    Errors = [errors.RowError]  # ✅ Fixed: Use proper error class

    def __init__(self, fields):
        super().__init__()  # ✅ Fixed: Add super() call
        self.fields = fields

    def validate_row(self, row):
        for field in self.fields:
            # Column missing → OK
            if field not in row:
                continue

            value = row[field]

            # Empty value → OK (optional)
            if value in (None, ""):
                continue

            # Invalid value → ERROR
            if value not in ("Yes", "No", "yes", "no"):
                note = f"Field '{field}' must be Yes or No when present, got: '{value}'"
                yield errors.RowError.from_row(row, note=note)  # ✅ Fixed: Use proper error creation

class ValidatePotentialNeedIndicated(Check):
    """Custom check for POTENTIAL_NEED_INDICATED based on ANSWER_CODE values"""
    code = "validate-potential-need-indicated"
    Errors = [errors.RowError]

    def __init__(self):
        super().__init__()

        # ANSWER_CODE values that require POTENTIAL_NEED_INDICATED = "pos"
        self.positive_answer_codes = {
            "la31994-9", "la31995-6", "la31996-4", "la28580-1", "la31997-2",
            "la31998-0", "la31999-8", "la32000-4", "la32001-2", "la33-6",
            "la32002-0", "la28397-0", "la6729-3", "la31981-6", "la31982-4"
        }

        # ANSWER_CODE values that require POTENTIAL_NEED_INDICATED = "neg"
        self.negative_answer_codes = {
            "la32-8", "la31993-1", "la9-3", "la28398-8", "la31983-2"
        }

    def validate_row(self, row):
        # Check if required fields exist
        if "ANSWER_CODE" not in row or "POTENTIAL_NEED_INDICATED" not in row:
            return

        answer_code = row["ANSWER_CODE"]
        potential_need = row["POTENTIAL_NEED_INDICATED"]

        # Skip validation if either field is empty
        if not answer_code or not potential_need:
            return

        # POSITIVE validation
        if answer_code in self.positive_answer_codes:
            if "pos" not in potential_need:
                note = (
                    f"When ANSWER_CODE is '{answer_code.upper()}', "
                    f"POTENTIAL_NEED_INDICATED field must be set to 'POS', "
                    f"Received value: '{potential_need.upper()}'"
                )
                yield errors.RowError.from_row(row, note=note)

        # NEGATIVE validation
        if answer_code in self.negative_answer_codes:
            if "neg" not in potential_need:
                note = (
                    f"When ANSWER_CODE is '{answer_code.upper()}', "
                    f"POTENTIAL_NEED_INDICATED field must be set to 'NEG', "
                    f"Received value: '{potential_need.upper()}'"
                )
                yield errors.RowError.from_row(row, note=note)

# Custom Check Class for Date/Time Leap Year Validation
class ValidateLeapYearDates(Check):
    code = "validate_leap_year_dates"
    Errors = [errors.RowError]

    # Define date/time fields that need leap year validation
    DATETIME_FIELDS = [
        "FACILITY_LAST_UPDATED",
        "SCREENING_LAST_UPDATED",
        "CONSENT_LAST_UPDATED",
        "ENCOUNTER_LAST_UPDATED",
        "PATIENT_LAST_UPDATED",
        "SEXUAL_ORIENTATION_LAST_UPDATED",
        "ENCOUNTER_START_DATETIME",
        "ENCOUNTER_END_DATETIME",
        "CONSENT_DATE_TIME",
        "SCREENING_START_DATETIME",
        "SCREENING_END_DATETIME"
    ]

    DATE_FIELDS = [
        "PATIENT_BIRTH_DATE"
    ]

    def validate_row(self, row):
        # Check datetime fields (ISO 8601 format with timezone)
        for field_name in self.DATETIME_FIELDS:
            field_value = row.get(field_name)
            if field_value and not self._is_valid_datetime(field_value):
                note = f"Invalid date in '{field_name}': '{field_value}'. Date does not exist (check leap year, month days)."
                yield errors.RowError.from_row(row, note=note)

        # Check date fields (YYYY-MM-DD format) already checked in frictionless patten
        # for field_name in self.DATE_FIELDS:
        #     field_value = row.get(field_name)
        #     if field_value and not self._is_valid_date(field_value):
        #         note = f"Invalid date in '{field_name}': '{field_value}'. Date does not exist (check leap year, month days)."
        #         yield errors.RowError.from_row(row, note=note)

    def _is_valid_datetime(self, datetime_str):
        """Validate ISO 8601 datetime string with proper leap year checking"""
        try:
            # Extract date part from datetime string (before 'T')
            if 'T' not in datetime_str:
                return False
            date_part = datetime_str.split('T')[0]
            return self._is_valid_date(date_part)
        except Exception:
            return False

    def _is_valid_date(self, date_str):
        """Validate date string (YYYY-MM-DD) with proper leap year checking"""
        try:
            # Parse the date string
            year, month, day = map(int, date_str.split('-'))

            # Check basic ranges
            if year < 1900 or year > 2100:  # Reasonable year range
                return False
            if month < 1 or month > 12:
                return False
            if day < 1:
                return False

            # Check days in month with leap year consideration
            if month in [1, 3, 5, 7, 8, 10, 12]:  # 31-day months
                return day <= 31
            elif month in [4, 6, 9, 11]:  # 30-day months
                return day <= 30
            elif month == 2:  # February
                if calendar.isleap(year):
                    return day <= 29  # Leap year
                else:
                    return day <= 28  # Non-leap year

            return False
        except (ValueError, IndexError):
            return False


# Custom Check Class for Answer Code Validation
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
        
        ###########################

        # # Detect which flag fields are actually present in CSV files and sync schema
        # potential_flag_fields = ["VISIT_PART_2_FLAG", "VISIT_OMH_FLAG", "VISIT_OPWDD_FLAG"]
        # detected_flag_fields = []

        # for i, resource in enumerate(package.resources):
        #     if resource.name == "qe_admin_data":
        #         # print(f"\n🔍 Checking flag fields in {resource.name}")
        #         try:
        #             # Read the CSV header to see what fields are actually present
        #             with open(resource.path, 'r') as f:
        #                 header_line = f.readline().strip()
        #                 actual_headers = [h.strip() for h in header_line.split(',')]

        #             # print(f"📊 CSV has {len(actual_headers)} columns")

        #             # Find which flag fields are present in CSV
        #             present_flags = [field for field in potential_flag_fields if field in actual_headers]
        #             missing_flags = [field for field in potential_flag_fields if field not in actual_headers]

        #             if present_flags:
        #                 # print(f"✅ Found flag fields in CSV: {present_flags}")
        #                 detected_flag_fields.extend(present_flags)

        #             if missing_flags:
        #                 # print(f"🗑️  Flag fields missing from CSV: {missing_flags}")

        #                 # Remove missing flag fields from schema to avoid validation errors
        #                 current_fields = resource.schema.fields
        #                 filtered_fields = [field for field in current_fields
        #                                  if field.name not in missing_flags]

        #                 if len(filtered_fields) != len(current_fields):
        #                     # print(f"🔧 Removing {len(current_fields) - len(filtered_fields)} flag fields from schema")
        #                     resource.schema.fields = filtered_fields
        #                     # print(f"📊 Schema now has {len(filtered_fields)} fields")

        #         except Exception as e:
        #             print(f"⚠️  Could not read CSV headers: {e}")
        #         break
            ##########################

        # Transform and validate
        common_transform_steps = [ 
            ("ORGANIZATION_TYPE_CODE", "organization_type_code"), 
            ("ORGANIZATION_TYPE_CODE_SYSTEM", "organization_type_code_system"), 
            ("FACILITY_STATE", "facility_state"),
            ("ENCOUNTER_CLASS_CODE", "encounter_class_code"),             
            ("ENCOUNTER_CLASS_CODE_SYSTEM", "encounter_class_code_system"),
            ("ENCOUNTER_STATUS_CODE", "encounter_status_code"),            
            ("ENCOUNTER_STATUS_CODE_SYSTEM", "encounter_status_code_system"),            
            ("ENCOUNTER_TYPE_CODE_SYSTEM", "encounter_type_code_system"),
            ("CONSENT_STATUS", "consent_status"),
            ("SCREENING_STATUS_CODE", "screening_status_code"),            
            ("SCREENING_STATUS_CODE_SYSTEM", "screening_status_code_system"),
            ("SCREENING_LANGUAGE_CODE", "screening_language_code"),            
            ("SCREENING_LANGUAGE_CODE_SYSTEM", "screening_language_code_system"),
            ("SCREENING_ENTITY_ID_CODE_SYSTEM", "screening_entity_id_code_system"),
            ("SCREENING_CODE", "screening_code"),            
            ("SCREENING_CODE_SYSTEM", "screening_code_system"),            
            ("QUESTION_CODE_SYSTEM", "question_code_system"),
            ("ANSWER_CODE", "answer_code"),            
            ("ANSWER_CODE_SYSTEM", "answer_code_system"), 
            ("OBSERVATION_CATEGORY_SDOH_CODE", "observation_category_sdoh_code"),              
            ("DATA_ABSENT_REASON_CODE", "data_absent_reason_code"),            
            ("POTENTIAL_NEED_INDICATED", "potential_need_indicated"),
            ("ADMINISTRATIVE_SEX_CODE", "administrative_sex_code"),             
            ("ADMINISTRATIVE_SEX_CODE_SYSTEM", "administrative_sex_code_system"),
            ("SEX_AT_BIRTH_CODE", "sex_at_birth_code"),            
            ("SEX_AT_BIRTH_CODE_SYSTEM", "sex_at_birth_code_system"),
            ("STATE", "state"),  
            ("TELECOM_USE", "telecom_use"), 
            ("RACE_CODE", "RACE_CODE"),       
            ("RACE_CODE_SYSTEM", "race_code_system"),  
            ("ETHNICITY_CODE", "ethnicity_code"),          
            ("ETHNICITY_CODE_SYSTEM", "ethnicity_code_system"),
            ("PERSONAL_PRONOUNS_CODE", "personal_pronouns_code"),            
            ("PERSONAL_PRONOUNS_SYSTEM", "personal_pronouns_system"),
            ("GENDER_IDENTITY_CODE", "gender_identity_code"),            
            ("GENDER_IDENTITY_CODE_SYSTEM", "gender_identity_code_system"),
            ("PREFERRED_LANGUAGE_CODE", "preferred_language_code"),            
            ("PREFERRED_LANGUAGE_CODE_SYSTEM", "preferred_language_code_system"), 
            ("SEXUAL_ORIENTATION_CODE", "sexual_orientation_code"),            
            ("SEXUAL_ORIENTATION_CODE_SYSTEM", "sexual_orientation_code_system")            
        ]

        # Track errors to avoid duplicates
        seen_errors = set()

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

                # Create a unique key for this error to avoid duplicates
                error_key = (missing_field)

                if error_key not in seen_errors:
                    seen_errors.add(error_key)
                    file_info = f" in file '{file_in_error}'" if file_in_error else ""
                    user_friendly_message = (
                        f"The field '{missing_field}' is missing or incorrectly named in the dataset. "
                        f"Please check if it exists in the CSV file and matches the expected schema."
                    )
                    results["errorsSummary"].append({
                        "fieldName": missing_field,
                        "message": user_friendly_message,
                        "type": "data-processing-errors"
                    })
       

        # Create checklist with only detected flag fields
        checks = [ValidateAnswerCode()]

        # Only add flag validation if flag fields were detected
        # if detected_flag_fields:
        #     # print(f"🔍 Adding flag validation for: {detected_flag_fields}")
        #     checks.insert(0, OptionalYesNoFlagsCheck(detected_flag_fields))
        # else:
        #     print("ℹ️  No flag fields detected - skipping flag validation")

        checklist = Checklist(checks=checks)
        # Validate the package - frictionless will handle optional fields marked with required: false
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