import json 
from frictionless import Package, Report

def validate_package(package_path, output_path):
    # Load the data package
    package = Package(package_path)

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
