# Use Debian 11 (Bullseye) slim as the base image
FROM debian:bullseye-slim
RUN echo "1"
# Avoid prompts from apt during build
ENV DEBIAN_FRONTEND=noninteractive

# Install necessary packages
RUN apt-get update && \
    apt-get install -y curl unzip wget sqlite3 git cron netcat procps && \
    rm -rf /var/lib/apt/lists/*

# Install Deno
ARG DENO_VERSION=1.41.0
RUN curl -fsSL https://deno.land/x/install/install.sh | DENO_VERSION=$DENO_VERSION sh
ENV PATH="/root/.deno/bin:$PATH"

# Install and setup DuckDB
RUN wget -qO- https://github.com/duckdb/duckdb/releases/download/v1.0.0/duckdb_cli-linux-amd64.zip > duckdb.zip && \
    unzip duckdb.zip -d /usr/local/bin/ && \
    chmod +x /usr/local/bin/duckdb && \
    rm duckdb.zip

# Clone the specified GitHub repository
WORKDIR /app
ARG REPO_URL
ARG TAG
ARG POSTGRES_DB
ARG POSTGRES_USER
ARG POSTGRES_HOST
ARG POSTGRES_PASSWORD
ARG POSTGRES_PORT

RUN git clone --depth 1 --branch $TAG $REPO_URL

# Install and check Deno dependencies
RUN deno cache ./1115-hub/src/ahc-hrsn-elt/screening/orchctl.ts && \
    deno check --config ./1115-hub/deno.jsonc ./1115-hub/src/ahc-hrsn-elt/screening/orchctl.ts

# Expose port 8082 for the health check server
EXPOSE 8082

# Environment variables
ARG QE_NAMES
ARG DATE
ARG INTERVAL
ARG ORCHCTL_CRON
ARG FHIR_ENDPOINT
ENV QE_NAMES=$QE_NAMES
ENV TAG=$TAG
ENV DATE=$DATE
ENV INTERVAL=$INTERVAL
ENV ORCHCTL_CRON=$ORCHCTL_CRON
ENV FHIR_ENDPOINT=$FHIR_ENDPOINT
ENV POSTGRES_DB=${POSTGRES_DB}
ENV POSTGRES_USER=${POSTGRES_USER}
ENV POSTGRES_HOST=${POSTGRES_HOST}
ENV POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
ENV POSTGRES_PORT=${POSTGRES_PORT}


# Copy the health check script
COPY health-check.sh /health-check.sh
RUN chmod +x /health-check.sh

RUN echo "Debug: QE_NAMES='$QE_NAMES', ORCHCTL_CRON='$ORCHCTL_CRON', FHIR_ENDPOINT='$FHIR_ENDPOINT'"

# Ensure that QE_NAMES, version, date, and ORCHCTL_CRON variables are provided
# Use bash to execute the command
RUN /bin/bash -c "if [[ -z \"$QE_NAMES\" || -z \"$ORCHCTL_CRON\" || -z \"$FHIR_ENDPOINT\" ]]; then \
        echo \"Environment variables QE_NAMES, TAG, DATE, DEPLOYMENT_DOMAIN, SEMAPHORE, ORCHCTL_CRON must be set.\"; \
        exit 1; \
    fi"

# Iterate over the QE_NAMES, treating it as a space-separated list 
RUN /bin/bash -c "IFS=' ' read -r -a qe_names_array <<< \"$QE_NAMES\";"

# define the file name and path
ENV crontab_file=/etc/cron.d/1115-hub
RUN touch $crontab_file


# add path to crontab
RUN echo "PATH=/usr/local/bin:/usr/bin:/bin" >> $crontab_file

# add .env file
RUN echo "POSTGRES_DB=${POSTGRES_DB}\nPOSTGRES_USER=${POSTGRES_USER}\nPOSTGRES_HOST=${POSTGRES_HOST}\nPOSTGRES_PASSWORD=${POSTGRES_PASSWORD}\nPOSTGRES_PORT=${POSTGRES_PORT}" > /app/1115-hub/.env

RUN /bin/bash -c "\
    IFS=' ' read -r -a qe_names_array <<< \"$QE_NAMES\"; \
    for qe_name in \${qe_names_array[@]}; do \
        QE_UPPER=\$(echo \$qe_name | tr '[:lower:]' '[:upper:]'); \
        echo \"\$ORCHCTL_CRON cd /app/1115-hub; /root/.deno/bin/deno run -A ./src/ahc-hrsn-elt/screening/orchctl.ts --qe \$qe_name --publish-fhir \$FHIR_ENDPOINT --publish-fhir-qe-id \$QE_UPPER >> /SFTP/observe/log/\$qe_name.log 2>&1\" >> \"\$crontab_file\"; \
    done"


RUN /bin/bash -c "echo '0 * * * * cd /app/1115-hub; /root/.deno/bin/deno run -A ./support/bin/doctor.ts >> /doctor_log.txt 2>&1' >> /etc/cron.d/1115-hub"

RUN crontab $crontab_file

# Create entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Set the entrypoint to run the entrypoint script
ENTRYPOINT ["/entrypoint.sh"]
CMD ["cron", "-f"]
