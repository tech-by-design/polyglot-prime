#!/bin/bash
 
# Ensure processingAgent is provided as a command-line argument
if [ -z "$1" ]; then
  echo "Usage: $0 <processingAgent>"
  exit 1
fi
 
# Assign the first argument to a variable
PROCESSING_AGENT="$1"
 
# Fetch secrets from AWS Secrets Manager and store them in temporary files
CERT_FILE=$(mktemp)
KEY_FILE=$(mktemp)
export AWS_DEFAULT_REGION=us-east-1
aws secretsmanager get-secret-value --secret-id techbd-qa-client-certificate --query 'SecretString' --output text > "$CERT_FILE"
aws secretsmanager get-secret-value --secret-id techbd-qa-client-key --query 'SecretString' --output text > "$KEY_FILE"
 
# Use curl to send the stdin payload to the endpoint with the specified processingAgent
curl --key "$KEY_FILE" \
     --cert "$CERT_FILE" \
     -s -X POST "https://qa.hrsn.nyehealth.org/HRSNBundle?processingAgent=${PROCESSING_AGENT}" \
     -H 'Content-Type: application/json' \
     --verbose \
     --data @-
 
# Capture the exit status of the curl command
CURL_EXIT_STATUS=$?
 
# Clean up temporary files
rm "$CERT_FILE" "$KEY_FILE"
 
# Return the curl exit status
exit $CURL_EXIT_STATUS
