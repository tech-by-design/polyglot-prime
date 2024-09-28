#!/bin/bash
 
# Ensure processingAgent is provided as a command-line argument
if [ -z "$1" ]; then
  echo "Usage: $0 <processingAgent>"
  exit 1
fi
 
# Assign the first argument to a variable
PROCESSING_AGENT="$1"
 
# Use curl to send the stdin payload to the endpoint with the specified processingAgent
curl -s -X POST "https://qa.hrsn.nyehealth.org/HRSNBundle?processingAgent=${PROCESSING_AGENT}" \
     -H 'Content-Type: application/json' \
     --verbose \
     --data @-
 
# Capture the exit status of the curl command
CURL_EXIT_STATUS=$?
 
# Clean up temporary files
rm "$CERT_FILE" "$KEY_FILE"
 
# Return the curl exit status
exit $CURL_EXIT_STATUS
