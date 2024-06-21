#!/bin/bash

# Correct way to start cron in the foreground
cron -f &

# Run the health check HTTP server in the foreground
/bin/bash /health-check.sh
