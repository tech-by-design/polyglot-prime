#!/bin/bash

# Script to copy techbd-udi-jooq-ingress.auto.jar from hub-prime/lib to all sub-modules' lib folders

SOURCE_JAR="hub-prime/lib/techbd-udi-jooq-ingress.auto.jar"

# Check if source file exists
if [ ! -f "$SOURCE_JAR" ]; then
    echo "Error: Source file $SOURCE_JAR not found!"
    exit 1
fi

echo "Copying $SOURCE_JAR to sub-modules..."

# List of target lib directories
TARGET_DIRS=(
    "core-lib/lib"
    "csv-service/lib"
    "fhir-validation-service/lib"
    "nexus-core-lib/lib"
)

# Copy to each target directory
for dir in "${TARGET_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "  → Copying to $dir/"
        cp "$SOURCE_JAR" "$dir/"
        if [ $? -eq 0 ]; then
            echo "    ✓ Success"
        else
            echo "    ✗ Failed"
        fi
    else
        echo "  ⚠ Directory $dir not found, skipping"
    fi
done

echo "Done!"
