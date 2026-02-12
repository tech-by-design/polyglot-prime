import json
import os
from frictionless import Package, transform, steps, extract
from datetime import datetime, date
from fastapi import FastAPI, File, UploadFile,APIRouter,HTTPException
import sys
import logging
import shutil
import re
import tempfile


logger = logging.getLogger("uvicorn")
logging.basicConfig(level=logging.DEBUG)
path_log = os.environ.get("PATH_LOG")
FORMAT = "%(filename)s - line:%(lineno)s - %(funcName)2s() -%(levelname)s %(asctime)-15s %(message)s"
logger = logging.getLogger(__name__)
if path_log:
    fh = logging.FileHandler(path_log)
    f = logging.Formatter(FORMAT)
    fh.setFormatter(f)
    logger.addHandler(fh)
logger.setLevel(logging.DEBUG)

spec_path = os.getenv("SPEC_PATH")
app = FastAPI()
validation_router =APIRouter() 


def custom_json_encoder(obj):
    if isinstance(obj, (datetime, date)):
        return obj.isoformat()
    raise TypeError(f"Object of type {obj.__class__.__name__} is not JSON serializable")

def _sanitize_upload_filename(expected_prefix: str, filename: str) -> str:
    if not filename:
        raise HTTPException(status_code=400, detail="Missing filename")
    base = os.path.basename(filename)
    if base != filename:
        raise HTTPException(status_code=400, detail="Invalid filename")
    if not base.startswith(expected_prefix):
        raise HTTPException(status_code=400, detail=f"Invalid file name. Must start with {expected_prefix}")
    if not re.fullmatch(r"[A-Za-z0-9_.\-]+", base):
        raise HTTPException(status_code=400, detail="Invalid filename")
    return base

def validate_package(spec_path, file1, file2, file3, file4):
    results = {
        "errorsSummary": [],
        "report": None,
        "originalData": {} 
    }

    try:
        with open(spec_path) as f:
            spec = json.load(f)
        file_mappings = {
            "qe_admin_data": file1,
            "screening_profile_data": file2,             
            "screening_observation_data": file3,
            "demographic_data": file4,  
        } 
        missing_files = {key: path for key, path in file_mappings.items() if not os.path.isfile(path)}
        if missing_files:
            for resource_name, file_path in missing_files.items():
                results["errorsSummary"].append({                                       
                    "fieldName": resource_name,
                    "message": f"File for resource '{resource_name}' not found: {file_path}",
                    "type": "file-missing-error"
                })
            return  
        
        if not results["errorsSummary"]:
            for resource_name, file_path in file_mappings.items():
                rows = extract(file_path) 
                results["originalData"][resource_name] = rows
                
        resources = []
        for resource in spec["resources"]:
            path = file_mappings.get(resource["name"])
            if not path:
                raise FileNotFoundError(f"File for resource '{resource['name']}' not found.")
            resource_with_path = {**resource, "path": path}
            resources.append(resource_with_path)
        package_descriptor = {
            "name": "csv-validation-using-ig",
            "resources": resources
        }
        package = Package(package_descriptor)
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
            transform_steps = [
                steps.cell_convert(field_name=field_name, function=lambda value: value.lower())
                for field_name, _ in common_transform_steps
                if any(field.name == field_name for field in resource.schema.fields)
            ]
            resource = transform(resource, steps=transform_steps)
        report = package.validate()
        results["report"] = report.to_dict()
    except FileNotFoundError as e:
        results["errorsSummary"].append({                        
            "fieldName": None,
            "message": str(e),
            "type": "file-missing-error"
        })
    except Exception as e:
        results["errorsSummary"].append({                        
            "fieldName": None,
            "message": str(e),
            "type": "unexpected-error"
        })
    return results
if __name__ == "__main__":
    results = {
        "errorsSummary": [],
        "report": None
    }
@validation_router.post("/validate_service_nyher_fhir_ig_equivalent/")
async def validate(
    QE_ADMIN_DATA_FILE: UploadFile = File(..., description="File should start with QE_ADMIN_DATA_"),
    SCREENING_PROFILE_DATA_FILE: UploadFile = File(..., description="File should start with SCREENING_PROFILE_DATA_"),
    SCREENING_OBSERVATION_DATA_FILE: UploadFile = File(..., description="File should start with SCREENING_OBSERVATION_DATA_"),
    DEMOGRAPHIC_DATA_FILE: UploadFile = File(..., description="File should start with DEMOGRAPHIC_DATA_")
    ):
    spec_path = os.getenv("SPEC_PATH")
    if not spec_path:
        logger.error("SPEC_PATH environment variable is not set.")
        return {"detail": "SPEC_PATH not set","status_code":400}
    logger.debug(f"SPEC_PATH: {spec_path}")
    if not os.path.isfile(spec_path):
        logger.error(f"Spec file not found at {spec_path}")
        return {"detail": f"Spec file not found at {spec_path}","status_code":400}
    file_validations = {
        "QE_ADMIN_DATA_FILE": QE_ADMIN_DATA_FILE.filename.startswith("QE_ADMIN_DATA_"),
        "SCREENING_PROFILE_DATA_FILE": SCREENING_PROFILE_DATA_FILE.filename.startswith("SCREENING_PROFILE_DATA_"),
        "SCREENING_OBSERVATION_DATA_FILE": SCREENING_OBSERVATION_DATA_FILE.filename.startswith("SCREENING_OBSERVATION_DATA_"),
        "DEMOGRAPHIC_DATA_FILE": DEMOGRAPHIC_DATA_FILE.filename.startswith("DEMOGRAPHIC_DATA_")
    }
    for file_key, is_valid in file_validations.items():
        if not is_valid:
            logger.error(f"Invalid file name for {file_key}. Must start with {file_key.replace('_FILE', '')}_")
            return {"detail": f"Invalid file name for {file_key}. Must start with {file_key.replace('_FILE', '')}_", "status_code":400}

    files = [
        (QE_ADMIN_DATA_FILE, "QE_ADMIN_DATA_"),
        (SCREENING_PROFILE_DATA_FILE, "SCREENING_PROFILE_DATA_"),
        (SCREENING_OBSERVATION_DATA_FILE, "SCREENING_OBSERVATION_DATA_"),
        (DEMOGRAPHIC_DATA_FILE, "DEMOGRAPHIC_DATA_"),
    ]

    with tempfile.TemporaryDirectory(prefix="nyher-fhir-ig-example-") as file_dir:
        temp_files = []
        for file, expected_prefix in files:
            safe_name = _sanitize_upload_filename(expected_prefix, file.filename)
            temp_file_path = os.path.join(file_dir, safe_name)
            logger.debug(f"Saving file {safe_name} to {temp_file_path}")
            try:
                with open(temp_file_path, "wb") as temp_file:
                    temp_file.write(await file.read())
                temp_files.append(temp_file_path)
                logger.debug(f"File {safe_name} saved successfully to {temp_file_path}")
            except Exception as e:
                logger.error(f"Failed to save file {safe_name}: {e}")
                raise HTTPException(status_code=500, detail=f"Failed to save {safe_name}")

        adjusted_temp_files = [os.path.join(file_dir, os.path.basename(path)) for path in temp_files]
        logger.debug(f"Adjusted file paths: {adjusted_temp_files}")
        try:
            logger.debug("Calling validate_package with spec file and temp files.")
            results = validate_package(spec_path, *adjusted_temp_files)
            logger.debug("Validation completed successfully.")
        except Exception as e:
            logger.error(f"Validation failed: {e}")
            results = {"error": "Validation process failed."}

        return results

