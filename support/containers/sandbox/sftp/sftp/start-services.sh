#!/bin/bash

# Start the health check endpoint in the background
/bin/bash /health-check.sh &

# Execute the original entrypoint script to start SFTP server with passed user parameters
exec /entrypoint "$@"