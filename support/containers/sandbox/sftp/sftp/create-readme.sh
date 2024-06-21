#!/bin/bash
echo "Debug: QE_NAMES='$QE_NAMES', TAG='$TAG', DATE='$DATE', ORCHCTL_CRON='$ORCHCTL_CRON', DEPLOYMENT_DOMAIN='$DEPLOYMENT_DOMAIN', SEMAPHORE='$SEMAPHORE'"

# Ensure that QE_NAMES, version, date, and ORCHCTL_CRON variables are provided
if [[ -z "$QE_NAMES" || -z "$TAG" || -z "$DATE" || -z "$ORCHCTL_CRON" || -z "$DEPLOYMENT_DOMAIN" || -z "$SEMAPHORE" ]]; then
    echo "Environment variables QE_NAMES, TAG, DATE, DEPLOYMENT_DOMAIN, SEMAPHORE, ORCHCTL_CRON must be set."
    exit 1
fi

# Iterate over the QE_NAMES, treating it as a space-separated list
IFS=' ' read -r -a qe_names_array <<< "$QE_NAMES"

# check if ORCHCTL_CRON has a slash in it, format in a way that is friendly to sed
if [[ "$ORCHCTL_CRON" == *"/"* ]]; then
    ORCHCTL_CRON=$(echo "$ORCHCTL_CRON" | sed 's/\//\\\//g')
fi

for qe_name in "${qe_names_array[@]}"; do
    # Define the output directory and create it if it doesn't exist
    output_dir="/home/$qe_name"
    mkdir -p "$output_dir"
    
    # this will get recreated by sftp startup
    rm -rf "$output_dir/ingress"

    # remove readme to be replaced
    rm -rf "$output_dir/README.md"

    # Process the template and replace variables
    sed "s/\${SEMAPHORE}/$SEMAPHORE/g; s/\${DEPLOYMENT_DOMAIN}/$DEPLOYMENT_DOMAIN/g; s/\${QE_NAME}/$qe_name/g; s/\${TAG}/$TAG/g; s/\${DATE}/$DATE/g; s/\${ORCHCTL_CRON}/$ORCHCTL_CRON/g" /README-template.md > "$output_dir/README.md"
done

echo "README files have been created."